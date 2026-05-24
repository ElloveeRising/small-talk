package com.ryan.smalltalk.tools

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DuckDuckGo HTML search (no API key). Returns up to 5 results, and auto-fetches the readable
 * article body of the top 2 so the model can ground its answer in real content instead of thin
 * search snippets. Never throws.
 */
class WebSearchTool(private val client: OkHttpClient) : Tool {

    override val name = "web_search"
    override val description = "Search the web for current or factual information."
    override val requiresNetwork = true

    private companion object {
        // Tuned for slow on-device CPUs: one deep article read keeps latency down AND keeps the
        // tool-result context small enough that the bigger E4B model can ingest it and still
        // generate within the pipeline timeout. Snippets from the other results carry breadth.
        const val MAX_RESULTS = 5
        const val DEEP_READ_COUNT = 1
        const val ARTICLE_CAP = 1200
        const val TOTAL_CAP = 2200
        const val FETCH_TIMEOUT_MS = 8_000L
    }

    private data class Hit(val title: String, val url: String, val snippet: String)

    override suspend fun execute(args: Map<String, String>): String = withContext(Dispatchers.IO) {
        val query = args["query"]?.trim().orEmpty()
        if (query.isEmpty()) return@withContext "No search query was provided."

        try {
            val searchUrl = "https://html.duckduckgo.com/html/?q=" +
                java.net.URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Android) SmallTalk")
                .build()

            val hits = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext "Web search failed (HTTP ${resp.code})."
                val doc = Jsoup.parse(resp.body?.string().orEmpty())
                doc.select("div.result").take(MAX_RESULTS).mapNotNull { el ->
                    val title = el.selectFirst("a.result__a")?.text()?.trim()
                    if (title.isNullOrEmpty()) return@mapNotNull null
                    val href = el.selectFirst("a.result__a")?.attr("href").orEmpty()
                    val snippet = el.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
                    Hit(title, decodeDdgUrl(href), snippet)
                }
            }

            if (hits.isEmpty()) return@withContext "No web results found for \"$query\"."

            val today = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
            val sb = StringBuilder("Web results for \"$query\" (fetched $today):\n")
            hits.forEachIndexed { i, h ->
                sb.append("\n[${i + 1}] ${h.title} — ${h.url}\n")
                if (h.snippet.isNotEmpty()) sb.append("${h.snippet}\n")
                if (i < DEEP_READ_COUNT && h.url.startsWith("http")) {
                    val body = fetchArticleBody(h.url, ARTICLE_CAP)
                    if (body.isNotEmpty()) sb.append("Article excerpt: $body\n")
                }
            }

            val out = sb.toString()
            if (out.length > TOTAL_CAP) out.take(TOTAL_CAP).trimEnd() + "…" else out
        } catch (e: Exception) {
            Log.w("WebSearchTool", "search failed", e)
            "Web search was unavailable: ${e.message}"
        }
    }

    /**
     * DuckDuckGo HTML anchors are redirect links like `//duckduckgo.com/l/?uddg=<encoded>&…`.
     * Pull the real destination out of the `uddg` param; fall back to the raw href.
     */
    private fun decodeDdgUrl(href: String): String {
        return try {
            val marker = "uddg="
            val start = href.indexOf(marker)
            if (start == -1) {
                if (href.startsWith("//")) "https:$href" else href
            } else {
                val raw = href.substring(start + marker.length)
                val end = raw.indexOf('&')
                URLDecoder.decode(if (end == -1) raw else raw.substring(0, end), "UTF-8")
            }
        } catch (e: Exception) {
            href
        }
    }

    /** Best-effort article body fetch. Bounded by a hard timeout; returns "" on any failure. */
    private suspend fun fetchArticleBody(url: String, cap: Int): String =
        withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android) SmallTalk")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use ""
                    WebContent.extractReadable(resp.body?.string().orEmpty(), cap).body
                }
            } catch (e: Exception) {
                ""
            }
        } ?: ""
}
