package com.ryan.smalltalk.tools

/**
 * v2 STUB — DO NOT IMPLEMENT IN v1.
 *
 * The primary planned addition for iteration 2 is a screen-reading tool backed by Android's
 * MediaProjection API. It is intentionally scaffolded here and registered through the same
 * [Tool] / ToolExecutor seam as every other tool, so adding the real implementation in v2 is:
 *
 *   1. Fill in [execute] with a MediaProjection capture + on-device OCR / vision pass.
 *   2. Add the matching `@Tool` method in SmallTalkToolSet (already stubbed there).
 *   3. Add the FOREGROUND_SERVICE_MEDIA_PROJECTION permission + projection consent flow.
 *
 * No pipeline, ModelManager, or UI restructuring is required — that is the whole point of the
 * ToolExecutor abstraction (success criterion #8).
 */
class ScreenshotToolStub : Tool {
    override val name = "take_screenshot"
    override val description = "Capture and read the current screen. (Coming in v2 — not yet available.)"
    override val requiresNetwork = false

    override suspend fun execute(args: Map<String, String>): String {
        // TODO(v2): MediaProjection capture -> bitmap -> feed to the multimodal responder / OCR.
        return "Screen reading is not available yet — it's planned for Small Talk v2."
    }
}
