package com.ryan.smalltalk.llm

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.tool
import com.ryan.smalltalk.tools.SmallTalkToolSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

enum class ModelState { NOT_LOADED, LOADING, READY, ERROR }

data class ModelStatus(
    val state: ModelState = ModelState.NOT_LOADED,
    val error: String? = null,
)

data class PipelineStatus(
    val router: ModelStatus = ModelStatus(),
    val responder: ModelStatus = ModelStatus(),
    val lowMemoryWarning: String? = null,
    /** True if the responder loaded with image/vision support (false = text-only fallback). */
    val visionEnabled: Boolean = true,
) {
    /** The responder is the brain — the app is usable as soon as it's up. */
    val responderReady: Boolean
        get() = responder.state == ModelState.READY

    /** Router (tool dispatch) is an optional enhancement; chat works without it. */
    val routerReady: Boolean
        get() = router.state == ModelState.READY
}

private const val TAG = "ModelManager"

/** Roughly the peak RAM the two models need together (FunctionGemma ~0.5 GB + Gemma E2B ~3 GB). */
private const val REQUIRED_BYTES = 3_500_000_000L

/**
 * Owns the two warm model sessions for the whole app lifecycle. Both run on CPU: the Tensor G4
 * GPU path in LiteRT-LM 0.11.0 uses an OpenGL delegate that fails on this device
 * (CreateSharedMemoryManager unimplemented). The responder attempts CPU vision (Gemma 3n is
 * multimodal); if the native runtime rejects it at init, it falls back to a text-only session
 * and reports visionEnabled=false. Token/sampler values mirror Edge Gallery's model_allowlist.
 *
 * Concurrency: [responder] is reassigned only inside a load coroutine *before* READY/ERROR is
 * published. Readers (ChatPipeline) run only after READY and never overlap a (re)load — the UI
 * gates on responderReady/isStreaming — so the var is safe without extra synchronization.
 */
class ModelManager {

    val router = LiteRtModel(
        tag = "Router", useGpu = false, supportImage = false, maxTokens = 1024, temperature = 0.0,
    )
    var responder: LiteRtModel = LiteRtModel(
        tag = "Responder", useGpu = false, supportImage = true, maxTokens = 4000, temperature = 1.0,
    )
        private set

    private val _status = MutableStateFlow(PipelineStatus())
    val status: StateFlow<PipelineStatus> = _status.asStateFlow()

    fun memoryWarning(context: Context): String? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return if (info.availMem < REQUIRED_BYTES) {
            "Low memory: ~${info.availMem / 1_000_000} MB free, the models need ~3.5 GB. " +
                "Loading may fail or be slow — close other apps."
        } else null
    }

    /**
     * Loads the responder (required) and optionally the router (if [routerPath] is non-null).
     * [responderToolBridge] is baked into the responder's ConversationConfig so tool dispatch
     * works natively via @Tool methods for the app's lifetime.
     */
    suspend fun loadAll(
        context: Context,
        routerPath: String?,
        responderPath: String,
        routerSystemPrompt: String,
        responderSystemPrompt: String,
        responderToolBridge: SmallTalkToolSet,
    ) {
        _status.value = PipelineStatus(
            router = if (routerPath != null) ModelStatus(ModelState.LOADING) else ModelStatus(ModelState.ERROR),
            responder = ModelStatus(ModelState.LOADING),
            lowMemoryWarning = memoryWarning(context),
        )

        supervisorScope {
            if (routerPath != null) {
                launch {
                    try {
                        router.load(routerPath, routerSystemPrompt, emptyList())
                        _status.value = _status.value.copy(router = ModelStatus(ModelState.READY))
                    } catch (e: Exception) {
                        Log.e(TAG, "Router load failed", e)
                        _status.value = _status.value.copy(
                            router = ModelStatus(ModelState.ERROR, e.message ?: "Failed to load FunctionGemma")
                        )
                    }
                }
            }
            launch {
                loadResponderWithFallback(responderPath, responderSystemPrompt, responderToolBridge)
            }
        }
    }

    /**
     * Loads the responder with image/vision first; if the native runtime rejects CPU vision at
     * init, closes it and retries text-only, publishing visionEnabled=false. Always starts from
     * a fresh vision-capable instance so an E2B↔E4B switch re-attempts vision.
     */
    private suspend fun loadResponderWithFallback(
        path: String,
        systemPrompt: String,
        toolBridge: SmallTalkToolSet,
    ) {
        runCatching { responder.close() }
        responder = LiteRtModel(
            tag = "Responder", useGpu = false, supportImage = true, maxTokens = 4000, temperature = 1.0,
        )
        try {
            responder.load(path, systemPrompt, listOf(tool(toolBridge)))
            _status.value = _status.value.copy(
                responder = ModelStatus(ModelState.READY), visionEnabled = true,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Responder load (vision) failed, retrying text-only", e)
            runCatching { responder.close() }
            responder = LiteRtModel(
                tag = "Responder", useGpu = false, supportImage = false, maxTokens = 4000, temperature = 1.0,
            )
            try {
                responder.load(path, systemPrompt, listOf(tool(toolBridge)))
                _status.value = _status.value.copy(
                    responder = ModelStatus(ModelState.READY), visionEnabled = false,
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Responder text-only load failed", e2)
                _status.value = _status.value.copy(
                    responder = ModelStatus(ModelState.ERROR, e2.message ?: "Failed to load Gemma responder"),
                    visionEnabled = false,
                )
            }
        }
    }

    /** Closes and reloads only the responder (e.g. when the user switches E2B ↔ E4B). */
    suspend fun reloadResponder(
        path: String,
        systemPrompt: String,
        toolBridge: SmallTalkToolSet,
    ) = withContext(Dispatchers.Default) {
        _status.value = _status.value.copy(responder = ModelStatus(ModelState.LOADING))
        loadResponderWithFallback(path, systemPrompt, toolBridge)
    }

    /**
     * Starts a fresh responder conversation on the same warm engine (no model reload, no
     * LOADING flicker). Used by the Refresh action to wipe the model's in-context memory.
     */
    suspend fun resetResponderConversation(
        systemPrompt: String,
        toolBridge: SmallTalkToolSet,
    ) = withContext(Dispatchers.Default) {
        if (!responder.isLoaded) return@withContext
        runCatching {
            responder.resetConversation(systemPrompt, listOf(tool(toolBridge)))
        }.onFailure { Log.e(TAG, "Responder conversation reset failed", it) }
    }

    suspend fun shutdown() = withContext(Dispatchers.Default) {
        router.close()
        responder.close()
        _status.value = PipelineStatus()
    }
}
