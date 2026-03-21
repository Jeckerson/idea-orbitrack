package io.orbitrack.idea.ui

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import io.orbitrack.idea.model.ItemState
import io.orbitrack.idea.model.ItemType
import java.time.Duration
import java.time.Instant
import javax.swing.JList

class ItemCellRenderer : ColoredListCellRenderer<ListEntry>() {

    /** IDE-standard base left inset for list cells. */
    private val baseLeft = JBUI.scale(2)
    private val baseVert = JBUI.scale(1)

    override fun customizeCellRenderer(
        list: JList<out ListEntry>,
        value: ListEntry?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        if (value == null) return

        // Reset enabled state so Item cells don't inherit disabled look from Header cells
        isEnabled = true

        when (value) {
            is ListEntry.Header -> {
                ipad = JBUI.insets(baseVert, baseLeft + JBUI.scale(16 * value.depth), baseVert, baseLeft)
                append(value.title, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.GRAY))
                isEnabled = false
            }
            is ListEntry.Item -> {
                ipad = JBUI.insets(baseVert, baseLeft + JBUI.scale(16 * value.depth), baseVert, baseLeft)
                renderItem(value.item)
            }
        }
    }

    private fun renderItem(value: io.orbitrack.idea.model.OrbiItem) {
        val typePrefix = if (value.type == ItemType.PR) "PR " else ""
        val stateColor = when (value.state) {
            ItemState.OPEN -> JBColor(0x1A7F37, 0x3FB950)
            ItemState.CLOSED -> JBColor(0xCF222E, 0xF85149)
            ItemState.MERGED -> JBColor(0x8250DF, 0xA371F7)
        }

        append(
            "${typePrefix}#${value.number} ",
            SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, stateColor)
        )
        append(value.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append("   ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append("${value.org}/${value.repo}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        if (value.labels.isNotEmpty()) {
            append(" \u00b7 ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            append(
                value.labels.joinToString(", "),
                SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, null)
            )
        }
        append(" \u00b7 @${value.author}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        append(" \u00b7 ${formatTimeAgo(value.updatedAt)}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    companion object {
        fun formatTimeAgo(instant: Instant): String {
            val duration = Duration.between(instant, Instant.now())
            return when {
                duration.toMinutes() < 1 -> "just now"
                duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
                duration.toHours() < 24 -> "${duration.toHours()}h ago"
                duration.toDays() < 30 -> "${duration.toDays()}d ago"
                else -> "${duration.toDays() / 30}mo ago"
            }
        }
    }
}
