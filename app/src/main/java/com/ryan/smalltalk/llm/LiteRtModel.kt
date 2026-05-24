package com.ryan.smalltalk.llm

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolProvider
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps a single LiteRT-LM [Engine] + [Conversation]. Mirrors the initialization and streaming
 * pattern used by Google AI Edge Gallery's LlmChatModelHelper (litertlm-android 0.11.0).
 *
 * One instance per model. Sessions are created once and kept warm for the app lifecycle —
 * cold init is expensive (see ModelManager).
 */
class LiteRtModel(
    private val tag: String,
    private val useGpu: Boolean,
    private val supportImage: Boolean,
    private val maxTokens: Int,
    private val temperature: Double,
) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    val isLoaded: Boolean
        get() = engine != null && conversation != null

    /**
     * Builds the engine and a warm conversation. Heavy and blocking — call off the main thread.
     * Throws on failure so the caller can surface a per-model error state.
     */
    suspend fun load(
        modelPath: String,
        systemInstruction: String?,
        toolProviders: List<ToolProvider>,
    ) = withContext(Dispatchers.Default) {
        val backend = if (useGpu) Backend.GPU() else Backend.CPU()
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            // CPU vision: the GPU delegate is broken on Tensor G4, but Gemma 3n is multimodal
            // and LiteRT-LM accepts a CPU vision backend. Caller falls back to text-only if the
            // native runtime rejects this at init.
            visionBackend = if (supportImage) Backend.CPU() else null,
            audioBackend = null,
            maxNumTokens = maxTokens,
            maxNumImages = if (supportImage) 1 else null,
            cacheDir = null,
        )
        Log.d(tag, "Initializing engine: path=$modelPath gpu=$useGpu image=$supportImage")
        val eng = Engine(engineConfig)
        eng.initialize()

        val conv = eng.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = 64,
                    topP = 0.95,
                    temperature = temperature,
                ),
                systemInstruction = systemInstruction?.let { Contents.of(it) },
                tools = toolProviders,
            )
        )
        engine = eng
        conversation = conv
        Log.d(tag, "Engine + conversation ready")
    }

    /**
     * Streams the model's response to [text] (+ optional [imageBytes]) as incremental text chunks.
     * Each emitted string is a delta to be appended by the collector.
     */
    fun streamMessage(
        text: String,
        imageBytes: ByteArray?,
        thinking: Boolean = false,
    ): Flow<String> = callbackFlow {
        val conv = conversation
        if (conv == null) {
            close(IllegalStateException("$tag conversation not initialized"))
            return@callbackFlow
        }

        val contents = buildList {
            if (imageBytes != null) add(Content.ImageBytes(imageBytes))
            if (text.isNotBlank()) add(Content.Text(text))
        }

        // `enable_thinking` is consumed by the Gemma 4 chat template (Jinja) via the
        // sendMessage extra-context map. Harmless if a model/template ignores it.
        val extraContext: Map<String, Any> =
            if (thinking) mapOf("enable_thinking" to true) else emptyMap()

        conv.sendMessageAsync(
            Contents.of(contents),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    trySend(message.toString())
                }

                override fun onDone() {
                    close()
                }

                override fun onError(throwable: Throwable) {
                    if (throwable is CancellationException) {
                        Log.i(tag, "Inference cancelled")
                        close()
                    } else {
                        Log.e(tag, "Inference error", throwable)
                        close(throwable)
                    }
                }
            },
            extraContext,
        )

        awaitClose { /* cancellation handled via cancel() */ }
    }

    /**
     * One-shot, non-streaming send used by the router: accumulates the full output and returns it.
     * Tool dispatch (LiteRT-LM native @Tool) happens during this call as a side effect.
     */
    suspend fun complete(text: String): String =
        suspendCancellableCoroutine { cont: CancellableContinuation<String> ->
            val conv = conversation
            if (conv == null) {
                cont.resumeWithException(IllegalStateException("$tag conversation not initialized"))
                return@suspendCancellableCoroutine
            }
            val sb = StringBuilder()
            conv.sendMessageAsync(
                Contents.of(listOf(Content.Text(text))),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        sb.append(message.toString())
                    }

                    override fun onDone() {
                        if (cont.isActive) cont.resume(sb.toString())
                    }

                    override fun onError(throwable: Throwable) {
                        if (throwable is CancellationException) {
                            if (cont.isActive) cont.resume(sb.toString())
                        } else if (cont.isActive) {
                            cont.resumeWithException(throwable)
                        }
                    }
                },
                emptyMap(),
            )
            cont.invokeOnCancellation { runCatching { conv.cancelProcess() } }
        }

    /**
     * Closes the current conversation and starts a fresh one on the same warm engine.
     * Used to keep the router stateless between queries (mirrors Edge Gallery's resetConversation).
     */
    suspend fun resetConversation(
        systemInstruction: String?,
        toolProviders: List<ToolProvider>,
    ) = withContext(Dispatchers.Default) {
        val eng = engine ?: return@withContext
        runCatching { conversation?.close() }
        conversation = eng.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = 64,
                    topP = 0.95,
                    temperature = temperature,
                ),
                systemInstruction = systemInstruction?.let { Contents.of(it) },
                tools = toolProviders,
            )
        )
    }

    fun cancel() {
        runCatching { conversation?.cancelProcess() }
    }

    fun close() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
    }
}
