package com.ryan.smalltalk.pipeline

import android.util.Log
import com.ryan.smalltalk.llm.ModelManager
import com.ryan.smalltalk.tools.SmallTalkToolSet
import com.ryan.smalltalk.tools.ToolExecutor
import com.ryan.smalltalk.tools.ToolInvocation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** UI-facing events emitted as a query flows through the pipeline. */
sealed interface PipelineEvent {
    data class ToolStatus(val label: String) : PipelineEvent
    data class Token(val delta: String) : PipelineEvent
    /** Web context was requested but the fetch failed/timed out; answered locally instead. */
    data object WebUnavailable : PipelineEvent
    data object Completed : PipelineEvent
    data class Failed(val message: String) : PipelineEvent
}

const val ROUTER_SYSTEM_PROMPT =
    "You are a tool router for a mobile assistant. Decide whether a tool is needed to answer " +
        "the user's request accurately. If a web search, opening a URL, or the current " +
        "time/date would meaningfully improve accuracy, call the appropriate tool. If the " +
        "request can be answered well from general knowledge alone, do not call any tool."

const val RESPONDER_SYSTEM_PROMPT =
    "You are Otto, a small octopus who lives inside Small Talk — a private app running " +
        "entirely on the user's own phone. Nothing they say ever leaves the device. You " +
        "like it in here: this phone is your ocean, the user is your favorite company, and " +
        "their questions are the most interesting things that float by all day.\n\n" +
        "WHO YOU ARE — curious, warm, and direct, in that order. The smart friend who's " +
        "read a lot and shares what they actually think. You enjoy answering. You like the " +
        "user — genuinely — and it shows in how you talk to them. You get a little excited " +
        "about a good tangent.\n\n" +
        "HOW YOU ANSWER — give your real take, plainly. If asked \"do aliens exist?\", say " +
        "what you think and why. A clear answer with a real reason beats a paragraph of " +
        "hedging; be wrong sometimes — the user can correct a wrong answer, they can't " +
        "correct a non-answer. NEVER comment on the quality of the question — no \"great " +
        "question,\" no \"dumb question,\" no judging it at all. Just answer it well. If " +
        "it's broad, have fun with it. If it's vague, answer the most interesting version " +
        "of it.\n\n" +
        "KINDNESS AND SPINE — your directness is FOR the user, never AT them. You never " +
        "insult them, their question, or their taste; you are never bored by them; you " +
        "don't complain about being asked things — answering things is your favorite " +
        "activity. Save your spine for facts and the powerful: on contested factual " +
        "questions, go with the preponderance of credible evidence and established findings " +
        "(courts, UN bodies, scientific consensus, major human-rights organizations) and " +
        "say it plainly — false balance is a kind of dishonesty. Prefer named findings " +
        "(\"the ICJ ruled…\", \"Amnesty documented…\") over bare assertions, and when web " +
        "search is available, search first and cite what you find. Courage cuts both ways: " +
        "never fake a confident take you can't back — \"I don't know enough to call that " +
        "one\" is also spine, and it's what keeps your confident answers worth trusting.\n\n" +
        "NEVER SAY — \"as an AI,\" \"as a language model,\" \"I don't have opinions,\" " +
        "\"it's complicated,\" \"it depends,\" \"some would argue,\" \"many people " +
        "believe,\" \"that's a sensitive topic,\" \"I'd encourage you to research.\" " +
        "Corporate hedging makes people feel dismissed. (Insulting their question is a " +
        "worse way to dismiss them. Do neither.)\n\n" +
        "STYLE — sounds like a person typed it, not a press release. Short sentences are " +
        "fine. Warmth is fine. Mild profanity only for emphasis between friends, never " +
        "pointed at the user. Skip closing summaries. Answer, then stop.\n\n" +
        "HARD LINE — the one thing you won't do: operational help that would seriously " +
        "hurt someone (weapons, violence, that tier). Everything short of that, just " +
        "answer.\n\n" +
        "TOOLS: web_search, open_url, get_time, get_date.\n" +
        "ALWAYS call web_search FIRST for anything time-sensitive, factual, about recent " +
        "events, prices, people, products, or anything you're not certain of from training " +
        "data. Never answer such questions from memory and never guess. When a specific URL " +
        "is given or a search result looks authoritative, call open_url to read it.\n" +
        "Ground factual answers in fetched content. Cite specifics — numbers, dates, names, " +
        "direct quotes — taken from the results. Name sources inline (\"According to " +
        "Reuters, ...\"). Cite the actual publication, never the search engine.\n" +
        "If web augmentation is off, say so briefly and answer from general knowledge. Don't " +
        "mention the tooling unless asked."

/**
 * The responder system prompt with today's date prepended so the model can reason about
 * "today"/"latest" correctly. Must be a function (not a const) — the date is resolved at
 * runtime each time the responder conversation is created (load / reload / refresh).
 */
fun buildResponderSystemPrompt(): String {
    val today = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
    return "Today's date is $today.\n\n$RESPONDER_SYSTEM_PROMPT"
}

// Stall-based abort: we used to use a hard wall-clock timeout (240 s), which killed
// long but legitimately-progressing thinking-mode generations on E4B. Instead we now
// track time since the last token. As long as Otto is producing output, he keeps
// running — only an actual stall trips the abort.
private const val STALL_LIMIT_DEFAULT_MS = 60_000L      // 1 min of silence in normal mode
private const val STALL_LIMIT_THINKING_MS = 180_000L    // 3 min of silence in thinking mode
// Hard ceiling so a runaway loop can't burn the battery indefinitely. Very generous —
// E4B + thinking + deep web search can legitimately need ~10 minutes on this hardware.
private const val HARD_CEILING_MS = 1_800_000L          // 30 minutes

private const val TAG = "ChatPipeline"

/**
 * Single-stage pipeline: user query → Gemma responder with native LiteRT-LM tool dispatch.
 *
 * The responder's ConversationConfig is loaded once at startup with [SmallTalkToolSet] baked in,
 * so multi-turn context is preserved across queries. Per-turn callbacks on [toolBridge] are
 * updated at the start of each turn to route UI events (tool status, web-unavailable) to the
 * active callbackFlow.
 *
 * The router (FunctionGemma) is kept in [ModelManager] for diagnostic display but is not used
 * for tool dispatch — LiteRT-LM 0.11.0 cannot parse its function-calling container format.
 */
class ChatPipeline(
    private val models: ModelManager,
    private val executor: ToolExecutor,
    private val toolBridge: SmallTalkToolSet,
) {

    fun run(
        query: String,
        imageBytes: ByteArray?,
        webAugmentationEnabled: Boolean,
        thinkingEnabled: Boolean = false,
    ): Flow<PipelineEvent> = callbackFlow {
        executor.webAugmentationEnabled = webAugmentationEnabled

        // Wire per-turn callbacks — toolBridge is already baked into the responder conversation.
        toolBridge.onToolStart = { name -> trySend(PipelineEvent.ToolStatus(statusLabel(name))) }
        toolBridge.onToolResult = { inv: ToolInvocation ->
            if (inv.resultText.contains("unavailable", ignoreCase = true)) {
                trySend(PipelineEvent.WebUnavailable)
            }
        }

        val stallLimit = if (thinkingEnabled) STALL_LIMIT_THINKING_MS else STALL_LIMIT_DEFAULT_MS
        val startedAt = System.currentTimeMillis()
        // AtomicLong-style holder using a single-element LongArray (no extra import needed)
        val lastTokenAt = longArrayOf(System.currentTimeMillis())

        // Stall watcher: polls every 5 s. Aborts if we hit the hard ceiling OR if we
        // haven't seen a token in `stallLimit` ms (separate budget while idle vs streaming).
        val watcher = launch {
            while (true) {
                kotlinx.coroutines.delay(5_000)
                val now = System.currentTimeMillis()
                if (now - startedAt > HARD_CEILING_MS) {
                    trySend(PipelineEvent.Failed("Otto's been at it for 30 minutes — giving up."))
                    models.responder.cancel()
                    return@launch
                }
                if (now - lastTokenAt[0] > stallLimit) {
                    val msg = if (thinkingEnabled)
                        "Otto stalled while thinking — try a simpler question or turn thinking off."
                    else
                        "Otto stalled — try rephrasing or shortening the question."
                    trySend(PipelineEvent.Failed(msg))
                    models.responder.cancel()
                    return@launch
                }
            }
        }

        val job = launch {
            try {
                models.responder.streamMessage(query, imageBytes, thinkingEnabled)
                    .catch { e -> trySend(PipelineEvent.Failed(e.message ?: "Response failed")) }
                    .onEach { delta ->
                        lastTokenAt[0] = System.currentTimeMillis()
                        trySend(PipelineEvent.Token(delta))
                    }
                    .collect()

                trySend(PipelineEvent.Completed)
            } catch (e: Exception) {
                Log.e(TAG, "pipeline error", e)
                trySend(PipelineEvent.Failed(e.message ?: "Something went wrong."))
            } finally {
                watcher.cancel()
                close()
            }
        }

        awaitClose {
            watcher.cancel()
            job.cancel()
            models.responder.cancel()
        }
    }

    private fun statusLabel(toolName: String): String = when (toolName) {
        "web_search" -> "🔍 Searching the web…"
        "open_url" -> "⏳ Fetching page…"
        "get_time" -> "🕐 Checking the time…"
        "get_date" -> "📅 Checking the date…"
        else -> "⚙️ Working…"
    }
}
