package com.ryan.smalltalk.tools

import org.jsoup.Jsoup

/**
 * Shared readable-text extraction used by both [OpenUrlTool] and [WebSearchTool]'s
 * auto-article-fetch. Strips boilerplate, collapses whitespace, caps length. Never throws.
 */
internal object WebContent {

    data class Extracted(val title: String, val body: String)

    fun extractReadable(html: String, cap: Int): Extracted = try {
        val doc = Jsoup.parse(html)
        doc.select("script, style, nav, footer, aside, header, noscript, form, svg, iframe").remove()
        val title = doc.title().trim()
        val raw = doc.body()?.text()?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val body = if (raw.length > cap) raw.take(cap).trimEnd() + "…" else raw
        Extracted(title, body)
    } catch (e: Exception) {
        Extracted("", "")
    }
}
