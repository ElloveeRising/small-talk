package com.ryan.smalltalk.tools

import android.util.Log
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Central registry + dispatcher for tools. This is the stable seam the pipeline talks to;
 * the LiteRT-LM native ToolSet (SmallTalkToolSet) is a thin annotated bridge that calls
 * straight into here. Registering a v2 tool requires no pipeline changes.
 */
class ToolExecutor(webAugmentationEnabled: Boolean) {

    var webAugmentationEnabled: Boolean = webAugmentationEnabled

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val tools: Map<String, Tool> = listOf(
        WebSearchTool(httpClient),
        OpenUrlTool(httpClient),
        GetTimeTool(),
        GetDateTool(),
        ScreenshotToolStub(), // v2 stub — registered, not active
    ).associateBy { it.name }

    fun descriptions(): List<Pair<String, String>> =
        tools.values.map { it.name to it.description }

    /**
     * Runs a tool by name. Never throws — returns an explanatory string on any failure or when
     * a network tool is invoked while web augmentation is disabled.
     */
    suspend fun run(name: String, args: Map<String, String>): String {
        val tool = tools[name] ?: return "Unknown tool: $name"
        if (tool.requiresNetwork && !webAugmentationEnabled) {
            return "Web augmentation is turned off, so \"$name\" could not run. " +
                "Answering from local knowledge only."
        }
        return try {
            tool.execute(args)
        } catch (e: Exception) {
            Log.w("ToolExecutor", "tool '$name' failed", e)
            "The \"$name\" tool failed: ${e.message}"
        }
    }
}
