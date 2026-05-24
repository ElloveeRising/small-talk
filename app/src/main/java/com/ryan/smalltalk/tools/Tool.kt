package com.ryan.smalltalk.tools

/**
 * A single capability the router model can invoke.
 *
 * This is the v2 extension seam (success criterion #8): adding a new tool — including the
 * planned MediaProjection screenshot tool — means writing one [Tool], registering it in
 * [ToolExecutor], and exposing one `@Tool` method in SmallTalkToolSet. No pipeline changes.
 */
interface Tool {
    /** Stable identifier, e.g. "web_search". Matches the SmallTalkToolSet method intent. */
    val name: String

    /** Human-readable summary used in the router's tool descriptions. */
    val description: String

    /** Whether this tool needs a network connection (gated by the web-augmentation toggle). */
    val requiresNetwork: Boolean

    /**
     * Executes the tool. Implementations must be self-contained and return a plain string
     * that will be injected into the responder's context. Must not throw — return an
     * explanatory string on failure so the pipeline can degrade gracefully.
     */
    suspend fun execute(args: Map<String, String>): String
}

/** Result of a tool run, surfaced to the pipeline so it can label context for the responder. */
data class ToolInvocation(
    val toolName: String,
    val resultText: String,
)
