package com.ryan.smalltalk.ui

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.smalltalk.SmallTalkApp
import com.ryan.smalltalk.llm.ModelDownloader
import com.ryan.smalltalk.llm.ModelFiles
import com.ryan.smalltalk.llm.PipelineStatus
import com.ryan.smalltalk.llm.ResponderVariant
import com.ryan.smalltalk.model.Message
import com.ryan.smalltalk.pipeline.PipelineEvent
import com.ryan.smalltalk.pipeline.ROUTER_SYSTEM_PROMPT
import com.ryan.smalltalk.pipeline.buildResponderSystemPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

enum class Screen { SETUP, LOADING, CHAT }

data class ChatUiState(
    val screen: Screen = Screen.SETUP,
    val pipeline: PipelineStatus = PipelineStatus(),
    val messages: List<Message> = emptyList(),
    val isStreaming: Boolean = false,
    val errorMessage: String? = null,
    val webAugmentation: Boolean = true,
    val thinking: Boolean = false,
    val activeVariant: ResponderVariant = ResponderVariant.E2B,
    val e4bAvailable: Boolean = false,
    val e8bAvailable: Boolean = false,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<SmallTalkApp>()
    private val TAG = "ChatViewModel"

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val thread = mutableListOf<Message>()

    init {
        val ctx = app.applicationContext
        _uiState.value = _uiState.value.copy(
            webAugmentation = app.webAugmentationEnabled,
            thinking = app.thinkingEnabled,
            activeVariant = ModelFiles.getResponderVariant(ctx),
            e4bAvailable = ModelFiles.getVariantPathIfReadable(ctx, ResponderVariant.E4B) != null,
            e8bAvailable = ModelFiles.getVariantPathIfReadable(ctx, ResponderVariant.E8B) != null,
        )
        viewModelScope.launch {
            thread += app.history.loadAll()
            _uiState.value = _uiState.value.copy(messages = thread.toList())
            decideStartScreen()
        }
        viewModelScope.launch {
            app.models.status.collectLatest { status ->
                _uiState.value = _uiState.value.copy(pipeline = status)
                // Responder is the brain — enter chat as soon as it's up. The router
                // (optional tool dispatch) may still be loading or may have failed; the
                // pipeline degrades to direct local answers without it.
                if (status.responderReady && _uiState.value.screen == Screen.LOADING) {
                    _uiState.value = _uiState.value.copy(screen = Screen.CHAT)
                }
            }
        }
    }

    private fun decideStartScreen() {
        val ctx = app.applicationContext
        // No permission check here: the standard app-private model needs none. isConfigured
        // resolves app-private, legacy-public, and custom paths in that order.
        val ready = ModelFiles.isConfigured(ctx)
        if (ready) startModelLoading() else _uiState.value = _uiState.value.copy(screen = Screen.SETUP)
    }

    fun refreshSetupState() = decideStartScreen()

    fun startModelLoading() {
        val ctx = app.applicationContext
        val router = ModelFiles.getRouterPath(ctx)   // nullable — router is optional
        val responder = ModelFiles.getResponderPath(ctx)
        if (responder == null) {
            _uiState.value = _uiState.value.copy(screen = Screen.SETUP)
            return
        }
        _uiState.value = _uiState.value.copy(screen = Screen.LOADING)
        viewModelScope.launch {
            app.models.loadAll(
                context = ctx,
                routerPath = router,
                responderPath = responder,
                routerSystemPrompt = ROUTER_SYSTEM_PROMPT,
                responderSystemPrompt = buildResponderSystemPrompt(),
                responderToolBridge = app.toolBridge,
            )
        }
    }

    /** Shared download state, surfaced in Settings for the E4B in-place download. */
    val downloaderState get() = app.downloader.state

    /** Fetch the heavier E4B brain from Settings — runs on the app scope so leaving the
     *  screen (or rotating) can't cancel a multi-GB transfer. */
    fun downloadE4B() {
        val ctx = app.applicationContext
        app.downloader.reset()
        app.appScope.launch {
            val path = app.downloader.download(
                ctx, ModelDownloader.E4B_URL, ResponderVariant.E4B.filename,
            )
            if (path != null) refreshVariantAvailability()
        }
    }

    fun refreshVariantAvailability() {
        val ctx = app.applicationContext
        _uiState.value = _uiState.value.copy(
            e4bAvailable = ModelFiles.getVariantPathIfReadable(ctx, ResponderVariant.E4B) != null,
            e8bAvailable = ModelFiles.getVariantPathIfReadable(ctx, ResponderVariant.E8B) != null,
        )
    }

    fun setWebAugmentation(enabled: Boolean) {
        app.webAugmentationEnabled = enabled
        _uiState.value = _uiState.value.copy(webAugmentation = enabled)
    }

    fun setThinking(enabled: Boolean) {
        app.thinkingEnabled = enabled
        _uiState.value = _uiState.value.copy(thinking = enabled)
    }

    fun switchResponder(variant: ResponderVariant) {
        val ctx = app.applicationContext
        ModelFiles.setResponderVariant(ctx, variant)
        val path = ModelFiles.getResponderPath(ctx) ?: return
        _uiState.value = _uiState.value.copy(activeVariant = variant)
        viewModelScope.launch {
            app.models.reloadResponder(path, buildResponderSystemPrompt(), app.toolBridge)
        }
    }

    fun sendMessage(text: String, imageUri: Uri?) {
        if (_uiState.value.isStreaming) return
        if (text.isBlank() && imageUri == null) return

        viewModelScope.launch {
            val userMsg = Message(role = "user", text = text.trim(), imageUri = imageUri)
            thread += userMsg
            app.history.append(userMsg)

            val placeholder = Message(role = "assistant", text = "", isStreaming = true)
            thread += placeholder
            _uiState.value = _uiState.value.copy(
                messages = thread.toList(), isStreaming = true, errorMessage = null,
            )

            val imageBytes = imageUri?.let { encodeImage(app.applicationContext, it) }
            var rawAccumulated = ""   // full stream including any <think> tags
            var accumulated = ""      // cleaned answer text shown in the bubble
            var status: String? = null
            var webNote = false

            fun pushPlaceholder(thinkingDisplay: String? = null) {
                val updated = placeholder.copy(
                    text = accumulated,
                    isStreaming = true,
                    toolStatus = if (accumulated.isEmpty() && thinkingDisplay.isNullOrBlank()) status else null,
                    thinkingDisplay = thinkingDisplay?.takeIf { it.isNotBlank() },
                )
                thread[thread.lastIndex] = updated
                _uiState.value = _uiState.value.copy(messages = thread.toList())
            }

            app.pipeline.run(text.trim(), imageBytes, app.webAugmentationEnabled, app.thinkingEnabled)
                .collectLatest { event ->
                    when (event) {
                        is PipelineEvent.ToolStatus -> { status = event.label; pushPlaceholder() }
                        is PipelineEvent.WebUnavailable -> webNote = true
                        is PipelineEvent.Token -> {
                            rawAccumulated += event.delta
                            accumulated = stripThinkBlocks(rawAccumulated)
                            // Live thinking content (between <think> and either </think> or
                            // the current stream tip). Shown above the answer in muted style.
                            val thinkingNow = extractLiveThinking(rawAccumulated)
                            pushPlaceholder(thinkingNow)
                        }
                        is PipelineEvent.Failed -> {
                            _uiState.value = _uiState.value.copy(errorMessage = event.message)
                        }
                        is PipelineEvent.Completed -> { /* finalized below */ }
                    }
                }

            val emptyHint = when (_uiState.value.activeVariant) {
                ResponderVariant.E2B ->
                    "_(No reply — that request may have been too heavy to run on-device. Try " +
                        "rephrasing, shortening it, or asking again.)_"
                else ->
                    "_(No reply — the heavier model can run out of room on this device. Try " +
                        "the same question on Gemma 4 E2B in Settings ⚙, or send a shorter message.)_"
            }
            val finalText = buildString {
                append(accumulated.ifBlank { emptyHint })
                if (webNote) append("\n\n_(web context was unavailable — answered locally)_")
            }
            val finalMsg = Message(role = "assistant", text = finalText)
            thread[thread.lastIndex] = finalMsg
            app.history.append(finalMsg)
            _uiState.value = _uiState.value.copy(messages = thread.toList(), isStreaming = false)
        }
    }

    /**
     * Full fresh chat: stop any inference, wipe the visible thread + persisted history, AND
     * reset the responder's in-context memory so it truly starts over.
     */
    fun refreshChat() {
        viewModelScope.launch {
            app.models.responder.cancel()
            app.history.clearAll()
            thread.clear()
            _uiState.value = _uiState.value.copy(messages = emptyList(), errorMessage = null)
            app.models.resetResponderConversation(buildResponderSystemPrompt(), app.toolBridge)
        }
    }

    fun clearHistory() = refreshChat()

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // Removes complete <think>...</think> blocks from streamed text.
    // While the block is still open (</think> not yet received), trims from <think> onward
    // so partial reasoning content never appears in the chat bubble.
    private fun stripThinkBlocks(raw: String): String {
        var text = raw.replace(Regex("<think>[\\s\\S]*?</think>"), "")
        val openIdx = text.indexOf("<think>")
        if (openIdx >= 0) text = text.substring(0, openIdx)
        return text.trimStart('\n').trimEnd()
    }

    private fun isInThinkBlock(raw: String): Boolean {
        val lastOpen = raw.lastIndexOf("<think>")
        val lastClose = raw.lastIndexOf("</think>")
        return lastOpen >= 0 && lastOpen > lastClose
    }

    /**
     * Returns the live thinking content for streaming display. Prefers the in-progress
     * (still-open) think block; otherwise returns the most recent completed block. Returns
     * null when there's no thinking content at all.
     */
    private fun extractLiveThinking(raw: String): String? {
        val lastOpen = raw.lastIndexOf("<think>")
        if (lastOpen < 0) return null
        val openContent = lastOpen + "<think>".length
        val closeIdx = raw.indexOf("</think>", startIndex = openContent)
        val content = if (closeIdx >= 0) {
            raw.substring(openContent, closeIdx)
        } else {
            raw.substring(openContent)
        }
        return content.trim().ifBlank { null }
    }

    private suspend fun encodeImage(context: Context, uri: Uri): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input) ?: return@use null
                    ByteArrayOutputStream().use { out ->
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        out.toByteArray()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "image encode failed", e)
                null
            }
        }
}
