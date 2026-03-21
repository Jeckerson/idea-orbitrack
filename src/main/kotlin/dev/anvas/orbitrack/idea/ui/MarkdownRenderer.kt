package dev.anvas.orbitrack.idea.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI

/**
 * Lightweight markdown-to-HTML converter for rendering issue bodies and comments.
 * Handles: headings, bold, italic, inline code, fenced code blocks, links, images, lists, blockquotes, horizontal rules.
 */
object MarkdownRenderer {

    fun toHtml(markdown: String): String {
        val fg = colorHex(JBColor.foreground())
        val bg = colorHex(JBColor.background())
        val linkColor = colorHex(JBColor(0x0969DA, 0x58A6FF))
        val codeBg = colorHex(JBColor(0xF6F8FA, 0x2D333B))
        val borderColor = colorHex(JBColor.border())

        // Use the IDE's native label font
        val nativeFont = JBUI.Fonts.label()
        val fontFamily = nativeFont.family
        val fontSize = nativeFont.size

        val body = renderBody(markdown, linkColor, codeBg, borderColor)

        // Only CSS1 properties supported by Swing's HTMLEditorKit
        return buildString {
            append("<html><head><style>")
            append("body { font-family: \"$fontFamily\"; font-size: ${fontSize}pt; color: $fg; background-color: $bg; margin: 0; padding: 4px; }")
            append("a { color: $linkColor; }")
            append("code { background-color: $codeBg; font-size: ${fontSize - 1}pt; }")
            append("pre { background-color: $codeBg; padding: 8px; margin-top: 4px; margin-bottom: 4px; font-size: ${fontSize - 1}pt; }")
            append("blockquote { margin-left: 8px; padding-left: 8px; color: gray; }")
            append("h1 { font-size: ${fontSize + 4}pt; margin-top: 8px; margin-bottom: 4px; }")
            append("h2 { font-size: ${fontSize + 2}pt; margin-top: 8px; margin-bottom: 4px; }")
            append("h3 { font-size: ${fontSize + 1}pt; margin-top: 6px; margin-bottom: 3px; }")
            append("ul { margin-top: 4px; margin-bottom: 4px; margin-left: 20px; }")
            append("ol { margin-top: 4px; margin-bottom: 4px; margin-left: 20px; }")
            append("li { margin-top: 2px; margin-bottom: 2px; }")
            append("p { margin-top: 4px; margin-bottom: 4px; }")
            append("</style></head><body>")
            append(body)
            append("</body></html>")
        }
    }

    private fun renderBody(md: String, linkColor: String, codeBg: String, borderColor: String): String {
        // Strip raw HTML tags from the source (GitHub bot comments often contain raw HTML)
        val cleaned = stripHtmlTags(md)

        val sb = StringBuilder()
        val lines = cleaned.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // Fenced code block
            if (line.trimStart().startsWith("```")) {
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // skip closing ```
                sb.append("<pre><code>").append(esc(codeLines.joinToString("\n"))).append("</code></pre>")
                continue
            }

            // Heading
            val headingMatch = HEADING_RE.find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = inline(headingMatch.groupValues[2])
                sb.append("<h$level>$text</h$level>")
                i++
                continue
            }

            // Horizontal rule
            if (HR_RE.matches(line.trim())) {
                sb.append("<hr>")
                i++
                continue
            }

            // Blockquote
            if (line.trimStart().startsWith("> ")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith("> ")) {
                    quoteLines.add(lines[i].trimStart().removePrefix("> "))
                    i++
                }
                sb.append("<blockquote>").append(inline(quoteLines.joinToString("<br>"))).append("</blockquote>")
                continue
            }

            // Unordered list
            if (UL_RE.matches(line.trimStart())) {
                sb.append("<ul>")
                while (i < lines.size && UL_RE.matches(lines[i].trimStart())) {
                    val item = lines[i].trimStart().replaceFirst(UL_STRIP_RE, "")
                    sb.append("<li>").append(inline(item)).append("</li>")
                    i++
                }
                sb.append("</ul>")
                continue
            }

            // Ordered list
            if (OL_RE.matches(line.trimStart())) {
                sb.append("<ol>")
                while (i < lines.size && OL_RE.matches(lines[i].trimStart())) {
                    val item = lines[i].trimStart().replaceFirst(OL_STRIP_RE, "")
                    sb.append("<li>").append(inline(item)).append("</li>")
                    i++
                }
                sb.append("</ol>")
                continue
            }

            // Blank line
            if (line.isBlank()) {
                sb.append("<br>")
                i++
                continue
            }

            // Regular paragraph
            sb.append("<p>").append(inline(line)).append("</p>")
            i++
        }
        return sb.toString()
    }

    /**
     * Process inline markdown: images, links, bold, italic, code, @mentions.
     * Uses placeholder substitution to prevent double-processing of URLs.
     */
    private fun inline(text: String): String {
        var s = esc(text)

        // Inline code (before everything else to protect code content)
        s = s.replace(INLINE_CODE_RE) { "<code>${it.groupValues[1]}</code>" }

        // Collect placeholders to protect already-processed URLs
        val placeholders = mutableMapOf<String, String>()
        var counter = 0

        // Images: ![alt](url) → clickable link (JEditorPane doesn't support img well)
        s = s.replace(IMAGE_RE) { m ->
            val alt = m.groupValues[1].ifBlank { "image" }
            val url = m.groupValues[2]
            val key = "\u0000PH${counter++}\u0000"
            placeholders[key] = """<a href="$url">[$alt]</a>"""
            key
        }

        // Links: [text](url)
        s = s.replace(LINK_RE) { m ->
            val linkText = m.groupValues[1]
            val url = m.groupValues[2]
            val key = "\u0000PH${counter++}\u0000"
            placeholders[key] = """<a href="$url">$linkText</a>"""
            key
        }

        // Bold
        s = s.replace(BOLD_STAR_RE, "<b>$1</b>")
        s = s.replace(BOLD_UNDER_RE, "<b>$1</b>")
        // Italic
        s = s.replace(ITALIC_STAR_RE, "<i>$1</i>")
        s = s.replace(ITALIC_UNDER_RE, "<i>$1</i>")
        // Strikethrough
        s = s.replace(STRIKE_RE, "<s>$1</s>")

        // Auto-link bare URLs (only those not already inside a placeholder)
        s = s.replace(BARE_URL_RE) { m ->
            val url = m.groupValues[1]
            val key = "\u0000PH${counter++}\u0000"
            // Truncate display of very long URLs
            val display = if (url.length > 80) url.take(77) + "..." else url
            placeholders[key] = """<a href="$url">$display</a>"""
            key
        }

        // @mentions → link to GitHub profile (but not inside placeholders or emails)
        s = s.replace(MENTION_RE) { m ->
            val user = m.groupValues[1]
            """<a href="https://github.com/$user">@$user</a>"""
        }

        // Restore placeholders
        for ((key, value) in placeholders) {
            s = s.replace(key, value)
        }

        return s
    }

    /** Strip raw HTML tags that GitHub bot comments often include */
    private fun stripHtmlTags(md: String): String {
        // Remove <img ...> tags, replace with alt text if present
        var s = md.replace(Regex("""<img\s[^>]*?alt=["']([^"']*)["'][^>]*/?>""", RegexOption.IGNORE_CASE)) { m ->
            val alt = m.groupValues[1]
            if (alt.isNotBlank()) "[$alt]" else ""
        }
        s = s.replace(Regex("""<img\s[^>]*/?>""", RegexOption.IGNORE_CASE), "")
        // Remove other common HTML tags but keep content
        s = s.replace(Regex("""</?(?:div|span|p|br|table|tr|td|th|thead|tbody|strong|em|b|i|u|s|details|summary)[^>]*>""", RegexOption.IGNORE_CASE), " ")
        // Remove remaining HTML tags
        s = s.replace(Regex("""<[^>]+>"""), "")
        return s.trim()
    }

    private fun esc(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun colorHex(color: java.awt.Color): String =
        String.format("#%02x%02x%02x", color.red, color.green, color.blue)

    // Pre-compiled regex patterns
    private val HEADING_RE = Regex("""^(#{1,3})\s+(.*)""")
    private val HR_RE = Regex("""^[-*_]{3,}$""")
    private val UL_RE = Regex("""^[-*+]\s+.*""")
    private val UL_STRIP_RE = Regex("""^[-*+]\s+""")
    private val OL_RE = Regex("""^\d+\.\s+.*""")
    private val OL_STRIP_RE = Regex("""^\d+\.\s+""")
    private val INLINE_CODE_RE = Regex("""`([^`]+)`""")
    private val IMAGE_RE = Regex("""!\[([^\]]*)]\(([^)]+)\)""")
    private val LINK_RE = Regex("""\[([^\]]+)]\(([^)]+)\)""")
    private val BOLD_STAR_RE = Regex("""\*\*(.+?)\*\*""")
    private val BOLD_UNDER_RE = Regex("""__(.+?)__""")
    private val ITALIC_STAR_RE = Regex("""\*(.+?)\*""")
    private val ITALIC_UNDER_RE = Regex("""_(.+?)_""")
    private val STRIKE_RE = Regex("""~~(.+?)~~""")
    private val BARE_URL_RE = Regex("""(?<!\href="|")\b(https?://[^\s<\u0000]+)""")
    private val MENTION_RE = Regex("""(?<![/\w])@([a-zA-Z0-9](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?)""")
}
