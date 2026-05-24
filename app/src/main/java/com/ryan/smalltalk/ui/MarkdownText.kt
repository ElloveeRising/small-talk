package com.ryan.smalltalk.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Minimal, dependency-free markdown renderer for assistant messages. Supports `**bold**`,
 * `# / ## / ###` headings, `- / * / •` bullets, `[label](url)` and bare http(s) links.
 *
 * Streaming-safe: any unmatched `**` / `[` / `(` is emitted literally and resolves once the
 * closing token streams in. Never throws — falls back to plain text on any parse issue.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    linkColor: Color = AccentColor,
    fontSize: TextUnit = 15.sp,
) {
    val annotated = rememberMarkdown(text, linkColor)
    Text(text = annotated, modifier = modifier, color = color, fontSize = fontSize)
}

private val INLINE = Regex(
    """\*\*(.+?)\*\*|\[([^\]]+)]\((https?://[^\s)]+)\)|(https?://[^\s)\]]+)"""
)

@Composable
private fun rememberMarkdown(text: String, linkColor: Color) = buildAnnotatedString {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    )
    val lines = text.split("\n")
    lines.forEachIndexed { idx, raw ->
        val headingLevel = when {
            raw.startsWith("### ") -> 3
            raw.startsWith("## ") -> 2
            raw.startsWith("# ") -> 1
            else -> 0
        }
        val bulletMatch = Regex("""^\s*[-*•]\s+""").find(raw)

        when {
            headingLevel > 0 -> {
                val content = raw.substring(raw.indexOf(' ') + 1)
                val size = when (headingLevel) { 1 -> 1.25f; 2 -> 1.15f; else -> 1.05f }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size.em)) {
                    appendInline(content, linkStyles)
                }
            }
            bulletMatch != null -> {
                append("•  ")
                appendInline(raw.substring(bulletMatch.range.last + 1), linkStyles)
            }
            else -> appendInline(raw, linkStyles)
        }
        if (idx != lines.lastIndex) append("\n")
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(
    text: String,
    linkStyles: TextLinkStyles,
) {
    var cursor = 0
    for (m in INLINE.findAll(text)) {
        if (m.range.first > cursor) append(text.substring(cursor, m.range.first))
        val bold = m.groupValues[1]
        val linkLabel = m.groupValues[2]
        val linkUrl = m.groupValues[3]
        val bareUrl = m.groupValues[4]
        when {
            bold.isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            linkUrl.isNotEmpty() ->
                withLink(LinkAnnotation.Url(linkUrl, linkStyles)) { append(linkLabel) }
            bareUrl.isNotEmpty() ->
                withLink(LinkAnnotation.Url(bareUrl, linkStyles)) { append(bareUrl) }
        }
        cursor = m.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}
