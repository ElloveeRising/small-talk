package com.ryan.smalltalk.tools

import android.util.Log
import com.google.ai.edge.litertlm.Tool as LiteRtTool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.runBlocking

/**
 * LiteRT-LM native tool bridge baked into the responder's ConversationConfig at load time.
 *
 * Each `@Tool` method is auto-dispatched by the LiteRT-LM runtime when the responder decides a
 * tool is needed. The method delegates to [ToolExecutor] and fires [onToolStart]/[onToolResult]
 * so the pipeline can emit UI events. Callbacks are mutable vars so they can be updated per turn
 * without recreating this object (which would require resetting the conversation and losing
 * multi-turn context).
 *
 * These methods are invoked on a LiteRT-LM worker thread (never main), so [runBlocking] around
 * the suspending tool work is acceptable and bounded by the 10 s OkHttp timeouts.
 */
class SmallTalkToolSet(private val executor: ToolExecutor) : ToolSet {

    @Volatile var onToolStart: (toolName: String) -> Unit = {}
    @Volatile var onToolResult: (ToolInvocation) -> Unit = {}

    private fun dispatch(toolName: String, args: Map<String, String>): Map<String, String> {
        Log.d("SmallTalkToolSet", "responder invoked '$toolName' args=$args")
        onToolStart(toolName)
        val result = runBlocking { executor.run(toolName, args) }
        onToolResult(ToolInvocation(toolName, result))
        return mapOf("result" to result)
    }

    @LiteRtTool(description = "Search the web (DuckDuckGo) for current events or factual info.")
    fun web_search(
        @ToolParam(description = "The search query.") query: String,
    ): Map<String, String> = dispatch("web_search", mapOf("query" to query))

    @LiteRtTool(description = "Open a specific URL and read its main readable text.")
    fun open_url(
        @ToolParam(description = "The full URL to open.") url: String,
    ): Map<String, String> = dispatch("open_url", mapOf("url" to url))

    @LiteRtTool(description = "Get the current device time.")
    fun get_time(): Map<String, String> = dispatch("get_time", emptyMap())

    @LiteRtTool(description = "Get the current device date.")
    fun get_date(): Map<String, String> = dispatch("get_date", emptyMap())

    // ---- v2 STUB ----
    // Registered through the same seam so iteration 2 only fills in ScreenshotToolStub.execute()
    // and removes the "not available" guard. No pipeline changes required.
    @LiteRtTool(description = "Capture and read the current screen. Not available until v2.")
    fun take_screenshot(): Map<String, String> = dispatch("take_screenshot", emptyMap())
}
