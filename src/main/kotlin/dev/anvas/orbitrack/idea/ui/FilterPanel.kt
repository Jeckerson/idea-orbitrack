package dev.anvas.orbitrack.idea.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.anvas.orbitrack.idea.cache.CachedFilterState
import dev.anvas.orbitrack.idea.model.ItemState
import dev.anvas.orbitrack.idea.model.ItemType
import dev.anvas.orbitrack.idea.model.OrbiItem
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class FilterPanel(
    private val onFilterChanged: () -> Unit
) : JPanel(GridBagLayout()) {

    private var suppressEvents = false

    /** Pending org/repo selections from cache, applied when the combo models are populated. */
    private var pendingOrg: String? = null
    private var pendingRepo: String? = null

    private val orgCombo = ComboBox(DefaultComboBoxModel(arrayOf("All Orgs")))
    private val repoCombo = ComboBox(DefaultComboBoxModel(arrayOf("All Repos")))
    private val typeCombo = ComboBox(DefaultComboBoxModel(arrayOf("All Types", "Issues", "PRs")))
    private val stateCombo = ComboBox(DefaultComboBoxModel(arrayOf("Open", "All States", "Closed", "Merged")))

    // --- Sort controls ---
    private val sortFieldCombo = ComboBox(DefaultComboBoxModel(SortField.entries.toTypedArray()))
    private var sortDirection = SortDirection.DESC
    private val sortDirButton = JButton(sortDirection.symbol).apply {
        toolTipText = sortDirection.label
        addActionListener {
            sortDirection = if (sortDirection == SortDirection.DESC) SortDirection.ASC else SortDirection.DESC
            text = sortDirection.symbol
            toolTipText = sortDirection.label
            if (!suppressEvents) onFilterChanged()
        }
    }
    private val sortPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
        isOpaque = false
        add(sortFieldCombo)
        add(sortDirButton)
    }

    // --- Multi-select grouping via checkbox popup ---
    private val groupChecks = linkedMapOf(
        GroupMode.BY_ORG to JCheckBoxMenuItem(GroupMode.BY_ORG.label),
        GroupMode.BY_REPO to JCheckBoxMenuItem(GroupMode.BY_REPO.label),
        GroupMode.BY_TYPE to JCheckBoxMenuItem(GroupMode.BY_TYPE.label, true),
    )
    private val groupButton = JButton("Group by \u25BE").apply {
        addActionListener {
            val popup = JPopupMenu()
            for ((_, checkItem) in groupChecks) {
                popup.add(checkItem)
            }
            popup.show(this, 0, height)
        }
    }

    /** Returns selected group modes in canonical order (BY_ORG → BY_REPO → BY_TYPE). */
    val groupModes: List<GroupMode>
        get() = GroupMode.entries.filter { groupChecks[it]?.isSelected == true }

    // --- Collapsible inner panel ---
    private val innerPanel = JPanel(GridBagLayout()).apply {
        isOpaque = false
    }

    /** Full-width toggle button at the top of the panel. */
    private val toggleButton = JButton().apply {
        isOpaque = false
        isFocusPainted = false
        horizontalAlignment = SwingConstants.LEFT
        border = JBUI.Borders.empty(2, 0, 2, 0)
        addActionListener {
            innerPanel.isVisible = !innerPanel.isVisible
            updateToggleButtonLabel()
            if (!suppressEvents) onFilterChanged()
        }
    }

    init {
        border = JBUI.Borders.empty(4, 8, 2, 8)
        isOpaque = false

        val labelInsets = Insets(2, 0, 0, 4)
        val comboInsets = Insets(0, 0, 4, 0)

        var row = 0
        fun addRow(label: String, component: JComponent) {
            innerPanel.add(JBLabel("$label:"), GridBagConstraints().apply {
                gridx = 0; gridy = row; anchor = GridBagConstraints.WEST; insets = labelInsets
            })
            row++
            innerPanel.add(component, GridBagConstraints().apply {
                gridx = 0; gridy = row; fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0; insets = comboInsets
            })
            row++
        }

        addRow("Org", orgCombo)
        addRow("Repo", repoCombo)
        addRow("Type", typeCombo)
        addRow("State", stateCombo)
        addRow("Sort", sortPanel)
        addRow("View", groupButton)

        orgCombo.addActionListener { if (!suppressEvents) { updateToggleButtonLabel(); onFilterChanged() } }
        repoCombo.addActionListener { if (!suppressEvents) { updateToggleButtonLabel(); onFilterChanged() } }
        typeCombo.addActionListener { if (!suppressEvents) { updateToggleButtonLabel(); onFilterChanged() } }
        stateCombo.addActionListener { if (!suppressEvents) { updateToggleButtonLabel(); onFilterChanged() } }
        sortFieldCombo.addActionListener { if (!suppressEvents) { updateToggleButtonLabel(); onFilterChanged() } }
        for ((_, checkItem) in groupChecks) {
            checkItem.addActionListener {
                updateGroupButtonLabel()
                updateToggleButtonLabel()
                if (!suppressEvents) onFilterChanged()
            }
        }

        // Reflect default grouping selection in button label
        updateGroupButtonLabel()

        // Layout: toggle button at top, collapsible inner panel below
        add(toggleButton, GridBagConstraints().apply {
            gridx = 0; gridy = 0; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0
            insets = Insets(0, 0, 4, 0)
        })
        add(innerPanel, GridBagConstraints().apply {
            gridx = 0; gridy = 1; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0
        })

        updateToggleButtonLabel()
    }

    // --- Summary helpers ---

    /**
     * Builds a compact string of non-default active filter selections,
     * e.g. "Closed · PRs · my-org" — capped at 40 chars with ellipsis.
     */
    internal fun buildSummary(): String {
        val parts = mutableListOf<String>()

        // State (default = index 0 "Open")
        if (stateCombo.selectedIndex != 0) {
            parts += stateCombo.selectedItem?.toString() ?: ""
        }
        // Type (default = index 0 "All Types")
        if (typeCombo.selectedIndex != 0) {
            parts += typeCombo.selectedItem?.toString() ?: ""
        }
        // Repo (default = index 0 "All Repos")  — more specific than org, show first
        if (repoCombo.selectedIndex != 0) {
            parts += repoCombo.selectedItem?.toString() ?: ""
        } else if (orgCombo.selectedIndex != 0) {
            // Org (default = index 0 "All Orgs")
            parts += orgCombo.selectedItem?.toString() ?: ""
        }
        // Sort — show when not default (UPDATED DESC)
        val currentField = sortFieldCombo.selectedItem as? SortField ?: SortField.UPDATED
        if (currentField != SortField.UPDATED || sortDirection != SortDirection.DESC) {
            parts += "${currentField.name.lowercase()} ${sortDirection.symbol}"
        }

        if (parts.isEmpty()) return ""

        val joined = parts.filter { it.isNotBlank() }.joinToString(" \u00B7 ")
        return if (joined.length > 40) joined.take(39) + "\u2026" else joined
    }

    private fun updateToggleButtonLabel() {
        val summary = buildSummary()
        toggleButton.text = if (innerPanel.isVisible) {
            "\u25BE Filters"
        } else {
            if (summary.isBlank()) "\u25B8 Filters" else "\u25B8 Filters \u00B7 $summary"
        }
    }

    private fun updateGroupButtonLabel() {
        val modes = groupModes
        groupButton.text = if (modes.isEmpty()) "Group by \u25BE"
        else modes.joinToString(" \u203A ") { it.label } + " \u25BE"
    }

    fun updateOrgRepoChoices(items: List<OrbiItem>) {
        suppressEvents = true
        try {
            val orgs = items.map { it.org }.distinct().sorted()
            val repos = items.map { "${it.org}/${it.repo}" }.distinct().sorted()

            // Use pending cached selection (from restoreFilterState) if available,
            // otherwise preserve the current selection.
            val targetOrg = pendingOrg ?: orgCombo.selectedItem
            orgCombo.model = DefaultComboBoxModel((listOf("All Orgs") + orgs).toTypedArray())
            if (targetOrg != null) orgCombo.selectedItem = targetOrg
            // Clear pending once a real model is available (orgs populated)
            if (pendingOrg != null && orgs.isNotEmpty()) pendingOrg = null

            val targetRepo = pendingRepo ?: repoCombo.selectedItem
            repoCombo.model = DefaultComboBoxModel((listOf("All Repos") + repos).toTypedArray())
            if (targetRepo != null) repoCombo.selectedItem = targetRepo
            if (pendingRepo != null && repos.isNotEmpty()) pendingRepo = null
        } finally {
            suppressEvents = false
        }
        updateToggleButtonLabel()
    }

    fun applyFilter(items: List<OrbiItem>): List<OrbiItem> {
        return items.filter { item ->
            val orgOk = orgCombo.selectedIndex == 0 || item.org == orgCombo.selectedItem
            val repoOk = repoCombo.selectedIndex == 0
                    || "${item.org}/${item.repo}" == repoCombo.selectedItem
            val typeOk = when (typeCombo.selectedIndex) {
                1 -> item.type == ItemType.ISSUE
                2 -> item.type == ItemType.PR
                else -> true
            }
            val stateOk = when (stateCombo.selectedIndex) {
                0 -> item.state == ItemState.OPEN
                2 -> item.state == ItemState.CLOSED
                3 -> item.state == ItemState.MERGED
                else -> true
            }
            orgOk && repoOk && typeOk && stateOk
        }
    }

    fun applySort(items: List<OrbiItem>): List<OrbiItem> {
        val field = sortFieldCombo.selectedItem as? SortField ?: SortField.UPDATED
        val comparator: Comparator<OrbiItem> = when (field) {
            SortField.UPDATED -> compareBy { it.updatedAt }
            SortField.CREATED -> compareBy { it.createdAt }
            SortField.ID -> compareBy { it.number }
        }
        return if (sortDirection == SortDirection.DESC) {
            items.sortedWith(comparator.reversed())
        } else {
            items.sortedWith(comparator)
        }
    }

    /** Captures the current filter/sort/group/collapse state for persistence. */
    fun getFilterState(): CachedFilterState {
        return CachedFilterState(
            selectedOrg = if (orgCombo.selectedIndex > 0) orgCombo.selectedItem as? String else null,
            selectedRepo = if (repoCombo.selectedIndex > 0) repoCombo.selectedItem as? String else null,
            typeIndex = typeCombo.selectedIndex,
            stateIndex = stateCombo.selectedIndex,
            sortField = (sortFieldCombo.selectedItem as? SortField)?.name ?: "UPDATED",
            sortDirection = sortDirection.name,
            groupModes = groupModes.map { it.name },
            filtersCollapsed = !innerPanel.isVisible,
        )
    }

    /** Restores filter/sort/group/collapse state from a cached snapshot. */
    fun restoreFilterState(state: CachedFilterState) {
        suppressEvents = true
        try {
            // Type & State are fixed-size combos — safe to set by index
            if (state.typeIndex in 0 until typeCombo.itemCount) {
                typeCombo.selectedIndex = state.typeIndex
            }
            if (state.stateIndex in 0 until stateCombo.itemCount) {
                stateCombo.selectedIndex = state.stateIndex
            }

            // Org & Repo models may not be populated yet — store as pending
            // so updateOrgRepoChoices can apply them when the real data arrives.
            pendingOrg = state.selectedOrg
            pendingRepo = state.selectedRepo

            // Sort
            val restoredField = try { SortField.valueOf(state.sortField) } catch (_: Exception) { SortField.UPDATED }
            sortFieldCombo.selectedItem = restoredField
            val restoredDir = try { SortDirection.valueOf(state.sortDirection) } catch (_: Exception) { SortDirection.DESC }
            sortDirection = restoredDir
            sortDirButton.text = sortDirection.symbol
            sortDirButton.toolTipText = sortDirection.label

            // Group modes
            val restoredModes = state.groupModes.mapNotNull {
                try { GroupMode.valueOf(it) } catch (_: Exception) { null }
            }.toSet()
            for ((mode, checkItem) in groupChecks) {
                checkItem.isSelected = mode in restoredModes
            }
            updateGroupButtonLabel()

            // Collapse state
            innerPanel.isVisible = !state.filtersCollapsed
        } finally {
            suppressEvents = false
        }
        updateToggleButtonLabel()
    }
}
