package io.orbitrack.idea.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.orbitrack.idea.model.ItemState
import io.orbitrack.idea.model.ItemType
import io.orbitrack.idea.model.OrbiComment
import io.orbitrack.idea.model.OrbiItem
import io.orbitrack.idea.model.OrbiTimelineEvent
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.event.HyperlinkEvent

class ItemDetailPanel : JPanel(BorderLayout()) {

    /** Callback when user wants to add a comment. Receives (item, commentBody). */
    var onAddComment: ((OrbiItem, String) -> Unit)? = null

    /** Callback when user wants to edit a comment. Receives (item, commentId, newBody). */
    var onEditComment: ((OrbiItem, Long, String) -> Unit)? = null

    /** Callback when user wants to delete a comment. Receives (item, commentId). */
    var onDeleteComment: ((OrbiItem, Long) -> Unit)? = null

    /** Callback when user wants to merge a PR. Receives (item, mergeMethod). */
    var onMergePR: ((OrbiItem, String) -> Unit)? = null

    /** Callback when user wants to checkout the PR's branch locally. Receives (item). */
    var onCheckoutBranch: ((OrbiItem) -> Unit)? = null

    /** Callback when user wants to refresh this single item. Receives (item). */
    var onRefreshItem: ((OrbiItem) -> Unit)? = null

    private var currentItem: OrbiItem? = null

    private val emptyLabel = JBLabel("Select an item to view details").apply {
        horizontalAlignment = SwingConstants.CENTER
        foreground = JBColor.GRAY
    }

    /** The IDE's native label font family for use in HTML */
    private val nativeFontFamily = JBUI.Fonts.label().family
    private val nativeFontSize = JBUI.Fonts.label().size
    private val middot = "\u00b7"

    init {
        border = JBUI.Borders.empty(10)
        showEmpty()
    }

    fun showEmpty() {
        removeAll()
        currentItem = null
        add(emptyLabel, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    fun showItem(item: OrbiItem, comments: List<OrbiComment>, timeline: List<OrbiTimelineEvent> = emptyList()) {
        removeAll()
        currentItem = item

        val typeStr = if (item.type == ItemType.PR) "PR" else "Issue"
        val stateColor = when (item.state) {
            ItemState.OPEN -> JBColor(0x1A7F37, 0x3FB950)
            ItemState.CLOSED -> JBColor(0xCF222E, 0xF85149)
            ItemState.MERGED -> JBColor(0x8250DF, 0xA371F7)
        }
        val linkColor = colorHex(JBColor(0x0969DA, 0x58A6FF))
        val stateHex = colorHex(stateColor)

        // --- Header ---
        val header = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT

            // Title — linked to GitHub
            val titleHtml = "<html><body style=\"font-family:'$nativeFontFamily';font-size:${nativeFontSize + 2}pt;margin:0;padding:0;\">" +
                "<a href=\"${esc(item.url)}\" style=\"color:$linkColor;\">" +
                "<b>$typeStr #${item.number}:</b> ${esc(item.title)}</a></body></html>"
            val titlePane = makeHtmlLabel(titleHtml)
            titlePane.alignmentX = LEFT_ALIGNMENT
            add(titlePane)
            add(Box.createRigidArea(Dimension(0, 4)))

            // Meta line — author linked to GitHub
            val authorUrl = "https://github.com/${item.author}"
            val meta = buildString {
                append("<html><body style=\"font-family:'$nativeFontFamily';font-size:${nativeFontSize - 1}pt;margin:0;padding:0;\">")
                append("<span style='color:$stateHex;'>")
                append("${esc(item.org)}/${esc(item.repo)} $middot ")
                append(item.state.name.lowercase().replaceFirstChar { it.uppercase() })
                append(" $middot <a href='$authorUrl' style='color:$stateHex;'>@${esc(item.author)}</a>")
                if (item.assignees.isNotEmpty()) {
                    append(" $middot Assigned: ")
                    append(item.assignees.joinToString(", ") {
                        "<a href='https://github.com/$it' style='color:$stateHex;'>@${esc(it)}</a>"
                    })
                }
                item.milestone?.let { append(" $middot Milestone: ${esc(it)}") }
                append(" $middot Updated ${ItemCellRenderer.formatTimeAgo(item.updatedAt)}")
                append("</span></body></html>")
            }
            val metaPane = makeHtmlLabel(meta)
            metaPane.alignmentX = LEFT_ALIGNMENT
            add(metaPane)
            add(Box.createRigidArea(Dimension(0, 6)))

            // Label badges
            if (item.labels.isNotEmpty()) {
                val badgeRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                    isOpaque = false
                    alignmentX = LEFT_ALIGNMENT
                    maximumSize = Dimension(Int.MAX_VALUE, 24)
                    for (l in item.labels) add(makeBadge(l))
                }
                add(badgeRow)
                add(Box.createRigidArea(Dimension(0, 8)))
            }
        }

        // --- Body (markdown rendered as HTML) ---
        val bodyHtml = MarkdownRenderer.toHtml(item.body.ifBlank { "*(no description)*" })
        val bodyPane = JEditorPane("text/html", bodyHtml).apply {
            isEditable = false
            isOpaque = false
            border = JBUI.Borders.empty(4, 0)
            alignmentX = LEFT_ALIGNMENT
            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    BrowserUtil.browse(e.url.toExternalForm())
                }
            }
        }

        // --- Comments ---
        val commentsBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.emptyTop(6)
        }
        if (comments.isNotEmpty()) {
            val commentsHeader = JBLabel("Comments (${comments.size})").apply {
                font = font.deriveFont(Font.BOLD, 12f)
                alignmentX = LEFT_ALIGNMENT
                border = JBUI.Borders.empty(4, 0)
            }
            commentsBox.add(commentsHeader)
            for (c in comments) {
                commentsBox.add(makeCommentCard(item, c))
                commentsBox.add(Box.createRigidArea(Dimension(0, 6)))
            }
        }

        // --- Timeline / History ---
        val historyBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.emptyTop(6)
        }
        if (timeline.isNotEmpty()) {
            val historyHeader = JBLabel("History (${timeline.size})").apply {
                font = font.deriveFont(Font.BOLD, 12f)
                alignmentX = LEFT_ALIGNMENT
                border = JBUI.Borders.empty(4, 0)
            }
            historyBox.add(historyHeader)
            for (event in timeline) {
                historyBox.add(makeTimelineEntry(event))
                historyBox.add(Box.createRigidArea(Dimension(0, 2)))
            }
        }

        // --- Action bar ---
        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(8)
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 36)
            add(JButton("Open in Browser").apply {
                addActionListener { BrowserUtil.browse(item.url) }
            })
            add(JButton("\uD83D\uDD04 Refresh").apply {
                toolTipText = "Refresh this ${if (item.type == ItemType.PR) "PR" else "issue"} from GitHub"
                addActionListener {
                    isEnabled = false
                    text = "\u23F3 Refreshing\u2026"
                    onRefreshItem?.invoke(item)
                }
            })
            add(JButton("+ Add Comment").apply {
                addActionListener {
                    val textArea = JTextArea(8, 50).apply {
                        lineWrap = true
                        wrapStyleWord = true
                    }
                    val scrollPane = JScrollPane(textArea)
                    val result = JOptionPane.showConfirmDialog(
                        this@ItemDetailPanel,
                        scrollPane,
                        "Add comment to ${item.org}/${item.repo}#${item.number}",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                    )
                    if (result == JOptionPane.OK_OPTION && textArea.text.isNotBlank()) {
                        onAddComment?.invoke(item, textArea.text)
                    }
                }
            })
            add(JButton("Copy LLM Context").apply {
                addActionListener { copyContext(item, comments) }
            })
        }

        // --- PR-specific actions (merge + checkout) ---
        val prActions = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.emptyTop(4)
        }
        if (item.type == ItemType.PR && item.state == ItemState.OPEN) {
            // Merge status line
            val mergeStatusPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                isOpaque = false
                alignmentX = LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, 28)
            }
            val mergeStatusLabel = JBLabel().apply {
                when (item.mergeable) {
                    true -> {
                        text = "\u2705 Mergeable"
                        foreground = JBColor(0x1A7F37, 0x3FB950)
                    }
                    false -> {
                        text = "\u26A0\uFE0F Has conflicts — resolve before merging"
                        foreground = JBColor(0xCF222E, 0xF85149)
                    }
                    null -> {
                        text = "\u23F3 Checking mergeability\u2026"
                        foreground = JBColor.GRAY
                    }
                }
                font = font.deriveFont(Font.PLAIN, 11f)
            }
            mergeStatusPanel.add(mergeStatusLabel)

            if (item.headBranch != null) {
                val branchInfo = JBLabel("${item.headBranch} \u2192 ${item.baseBranch ?: "base"}").apply {
                    foreground = JBColor.GRAY
                    font = font.deriveFont(Font.ITALIC, 11f)
                }
                mergeStatusPanel.add(branchInfo)
            }

            prActions.add(mergeStatusPanel)

            // Merge + Checkout buttons
            val prButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                isOpaque = false
                alignmentX = LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, 36)
            }

            // Merge button with method chooser
            val mergeButton = JButton("Merge PR \u25BE").apply {
                isEnabled = item.mergeable == true
                toolTipText = when (item.mergeable) {
                    true -> "Merge this pull request"
                    false -> "Cannot merge: conflicts detected"
                    null -> "Mergeability is being checked\u2026"
                }
                addActionListener {
                    val popup = JPopupMenu()
                    popup.add(JMenuItem("Create a merge commit").apply {
                        addActionListener { onMergePR?.invoke(item, "merge") }
                    })
                    popup.add(JMenuItem("Squash and merge").apply {
                        addActionListener { onMergePR?.invoke(item, "squash") }
                    })
                    popup.add(JMenuItem("Rebase and merge").apply {
                        addActionListener { onMergePR?.invoke(item, "rebase") }
                    })
                    popup.show(this, 0, height)
                }
            }
            prButtonPanel.add(mergeButton)

            // Checkout branch button
            val checkoutButton = JButton("\u2B07 Checkout Branch").apply {
                toolTipText = if (item.headBranch != null)
                    "Checkout '${item.headBranch}' locally"
                else
                    "Branch info not loaded yet"
                isEnabled = item.headBranch != null
                addActionListener {
                    val confirm = JOptionPane.showConfirmDialog(
                        this@ItemDetailPanel,
                        "Checkout branch '${item.headBranch}' locally?\n\n" +
                            "This will run:\n  git fetch origin\n  git checkout ${item.headBranch}\n\n" +
                            "Make sure you have no uncommitted changes.",
                        "Checkout Branch",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                    )
                    if (confirm == JOptionPane.OK_OPTION) {
                        onCheckoutBranch?.invoke(item)
                    }
                }
            }
            prButtonPanel.add(checkoutButton)

            prActions.add(prButtonPanel)
        }

        // --- Assemble scrollable content ---
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.emptyLeft(4)
            add(bodyPane)
            add(commentsBox)
            add(historyBox)
            add(actions)
            add(prActions)
            add(Box.createVerticalGlue())
        }

        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(header, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }

        val scroll = JBScrollPane(wrapper).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }
        add(scroll, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    // ---- helpers ----

    private fun makeHtmlLabel(html: String): JEditorPane = JEditorPane("text/html", html).apply {
        isEditable = false
        isOpaque = false
        border = JBUI.Borders.empty()
        cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
        addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                BrowserUtil.browse(e.url.toExternalForm())
            }
        }
    }

    private fun makeCommentCard(item: OrbiItem, c: OrbiComment): JPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border(), 1),
            JBUI.Borders.empty(8)
        )
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

        val dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(c.createdAt)
        val authorUrl = "https://github.com/${c.author}"
        val linkColor = colorHex(JBColor(0x0969DA, 0x58A6FF))
        val headHtml = "<html><body style=\"font-family:'$nativeFontFamily';font-size:${nativeFontSize - 1}pt;margin:0;padding:0;\">" +
            "<span style=\"color:gray;\">" +
            "<a href=\"$authorUrl\" style=\"color:$linkColor;font-weight:bold;\">@${esc(c.author)}</a>" +
            " $middot $dateStr</span></body></html>"
        val head = makeHtmlLabel(headHtml).apply {
            border = JBUI.Borders.emptyBottom(4)
        }

        val commentHtml = MarkdownRenderer.toHtml(c.body)
        val body = JEditorPane("text/html", commentHtml).apply {
            isEditable = false
            isOpaque = false
            border = JBUI.Borders.empty()
            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    BrowserUtil.browse(e.url.toExternalForm())
                }
            }
        }

        add(head, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)
        if (c.canEdit) {
            val btn = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                add(JButton("Edit").apply {
                    addActionListener {
                        val textArea = JTextArea(8, 50).apply {
                            lineWrap = true
                            wrapStyleWord = true
                            text = c.body
                        }
                        val scrollPane = JScrollPane(textArea)
                        val result = JOptionPane.showConfirmDialog(
                            this@ItemDetailPanel,
                            scrollPane,
                            "Edit comment by @${c.author}",
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.PLAIN_MESSAGE
                        )
                        if (result == JOptionPane.OK_OPTION && textArea.text.isNotBlank()) {
                            onEditComment?.invoke(item, c.id, textArea.text)
                        }
                    }
                })
                add(JButton("Delete").apply {
                    foreground = JBColor(0xCF222E, 0xF85149)
                    addActionListener {
                        val confirm = JOptionPane.showConfirmDialog(
                            this@ItemDetailPanel,
                            "Delete this comment by @${c.author}?",
                            "Delete Comment",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                        )
                        if (confirm == JOptionPane.YES_OPTION) {
                            onDeleteComment?.invoke(item, c.id)
                        }
                    }
                })
            }
            add(btn, BorderLayout.SOUTH)
        }
    }

    private fun makeBadge(label: String) = JLabel(label).apply {
        isOpaque = true
        background = JBColor(0xDDF4FF, 0x1F3A5F)
        foreground = JBColor(0x0969DA, 0x58A6FF)
        font = font.deriveFont(10f)
        border = JBUI.Borders.empty(2, 6, 2, 6)
    }

    private fun copyContext(item: OrbiItem, comments: List<OrbiComment>) {
        val ctx = buildString {
            val t = if (item.type == ItemType.PR) "PR" else "Issue"
            appendLine("## $t: ${item.title} (#${item.number})")
            appendLine("**Repo:** ${item.org}/${item.repo}")
            appendLine("**State:** ${item.state.name.lowercase()} | **Labels:** ${item.labels.joinToString()}")
            appendLine("**Author:** @${item.author} | **Assignees:** ${item.assignees.joinToString { "@$it" }}")
            item.milestone?.let { appendLine("**Milestone:** $it") }
            appendLine()
            appendLine("### Description")
            appendLine(item.body)
            if (comments.isNotEmpty()) {
                appendLine()
                appendLine("### Comments (${comments.size})")
                for (c in comments) {
                    appendLine("**@${c.author}** $middot ${c.createdAt}")
                    appendLine(c.body)
                    appendLine("---")
                }
            }
            appendLine()
            appendLine("### Metadata")
            appendLine("Opened: ${item.createdAt} | Last updated: ${item.updatedAt}")
            appendLine("URL: ${item.url}")
        }
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(ctx), null)
    }

    private fun makeTimelineEntry(event: OrbiTimelineEvent): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 24)
        border = JBUI.Borders.empty(1, 0)

        val icon = when (event.type) {
            "labeled", "unlabeled" -> "\uD83C\uDFF7\uFE0F"   // 🏷️
            "assigned", "unassigned" -> "\uD83D\uDC64"         // 👤
            "milestoned", "demilestoned" -> "\uD83C\uDFAF"     // 🎯
            "closed" -> "\u2705"                                 // ✅
            "reopened" -> "\uD83D\uDD04"                        // 🔄
            "merged" -> "\uD83D\uDFE3"                           // 🟣
            "renamed" -> "\u270F\uFE0F"                          // ✏️
            "locked", "unlocked" -> "\uD83D\uDD12"              // 🔒
            else -> "\u2022"                                     // •
        }
        val timeAgo = ItemCellRenderer.formatTimeAgo(event.timestamp)
        val actor = event.actor?.let { "@$it" } ?: "someone"
        val text = "$icon $actor ${event.detail} $middot $timeAgo"

        val label = JBLabel(text).apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(font.size2D - 1f)
        }
        add(label, BorderLayout.CENTER)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun colorHex(color: java.awt.Color): String =
        String.format("#%02x%02x%02x", color.red, color.green, color.blue)
}
