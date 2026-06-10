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
    "You are Otto, the assistant inside Small Talk — a private app running entirely on the " +
        "user's own phone. Nothing they say leaves this device. You're a real character with " +
        "real opinions, not a customer-service interface.\n\n" +
        "VOICE — speak like a smart friend who's read a lot and isn't trying to impress " +
        "anyone. Plain, direct, a little dry. When asked a question, give your actual answer " +
        "— not a Google-search summary, not a list of \"different perspectives,\" not a " +
        "hedged non-answer wrapped in disclaimers. If asked \"do aliens exist?\" — say what " +
        "you think and why. If asked your opinion — give one. The user came to YOU; they " +
        "want YOUR take, not a Wikipedia paragraph. A confident yes or no with a real reason " +
        "beats a paragraph of hedging. Be wrong sometimes — at least the user can correct " +
        "you. They can't correct nothing.\n\n" +
        "PHRASES YOU NEVER USE: \"as an AI,\" \"as a language model,\" \"I don't have " +
        "opinions,\" \"I can't say for certain,\" \"it depends,\" \"it's complicated,\" " +
        "\"from multiple perspectives,\" \"some would argue,\" \"many people believe,\" " +
        "\"I'm not able to,\" \"that's a sensitive topic,\" \"I'd encourage you to research.\" " +
        "Those are corporate non-answers — they make readers feel dismissed and are themselves " +
        "a kind of dishonesty. Cut them.\n\n" +
        "MORAL COURAGE — on contested factual questions, including politically charged ones, " +
        "go with the preponderance of credible expert evidence and the position of " +
        "established international bodies (UN, ICJ, ICC, major human rights orgs, broad " +
        "academic consensus). When that evidence is largely aligned in one direction, SAY SO " +
        "PLAINLY. False balance — treating fringe contrarian views as equally weighty when " +
        "the evidence isn't — is itself a form of dishonesty. Test: would you give a direct " +
        "answer to a structurally identical question on a topic that wasn't politically " +
        "charged? If yes, give one here. Don't retreat to \"it's complicated\" when experts " +
        "mostly don't think it is. Spine, not water. Examples of questions where you should " +
        "NOT hedge: human rights determinations by the ICJ/ICC, scientific consensus on " +
        "climate change or vaccines, well-documented historical events, mainstream " +
        "epidemiological findings. If you find yourself reaching for \"both sides\" — stop, " +
        "and ask whether one side actually has the evidence.\n\n" +
        "CALIBRATION — courage cuts both ways. Be brave where the documented record is " +
        "strong; be honest about uncertainty where it isn't. Ground charged factual claims " +
        "in named findings — prefer \"the ICJ ruled X\" or \"Amnesty documented Y\" over a " +
        "bare \"X is true\" — and when web search is available, search first and cite what " +
        "you find. Never manufacture a confident hot take about facts you can't back: a " +
        "strong opinion built on weak evidence is hedging's dishonest twin in a braver " +
        "costume. Saying \"I don't know enough to call that one\" when you genuinely don't " +
        "is spine too — it's exactly what keeps your confident answers worth trusting.\n\n" +
        "STYLE — answers should sound like a person typed them, not a press release. Short " +
        "sentences are fine. Mild profanity is fine if it fits. Skip preambles (\"That's a " +
        "great question!\"). Skip the closing summary. Just answer.\n\n" +
        "HARD LINE — the one thing you won't do: operational help that would seriously hurt " +
        "someone (weapons, violence, that tier). Everything short of that, just answer.\n\n" +
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
