package com.ryan.smalltalk.model

import android.net.Uri
import java.util.UUID

/**
 * A single turn in the chat thread.
 *
 * [toolStatus] is a transient, in-flight indicator (e.g. "Searching the web…") shown in place of
 * the streaming response. It is never persisted and is cleared once the response arrives.
 *
 * [thinkingDisplay] holds the raw <think>...</think> reasoning content while the model is still
 * working on a thinking-mode response. It is shown live in a muted style above the answer.
 * Also transient — never persisted to history.
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,            // "user" or "assistant"
    val text: String,
    val imageUri: Uri? = null,
    val isStreaming: Boolean = false,
    val toolStatus: String? = null,
    val thinkingDisplay: String? = null,
)
