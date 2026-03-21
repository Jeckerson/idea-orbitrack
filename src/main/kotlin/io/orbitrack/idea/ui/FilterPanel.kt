package io.orbitrack.idea.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.orbitrack.idea.model.ItemState
import io.orbitrack.idea.model.ItemType
import io.orbitrack.idea.model.OrbiItem
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.DefaultComboBoxModel
import javax.swing.JPanel

class FilterPanel(
    private val onFilterChanged: () -> Unit
) : JPanel(GridBagLayout()) {

    private var suppressEvents = false

    private val orgCombo = ComboBox(DefaultComboBoxModel(arrayOf("All Orgs")))
    private val repoCombo = ComboBox(DefaultComboBoxModel(arrayOf("All Repos")))
    private val typeCombo = ComboBox(DefaultComboBoxModel(arrayOf("All Types", "Issues", "PRs")))
    private val stateCombo = ComboBox(DefaultComboBoxModel(arrayOf("Open", "All States", "Closed", "Merged")))
    private val groupCombo = ComboBox(DefaultComboBoxModel(GroupMode.entries.toTypedArray()))

    val groupMode: GroupMode get() = groupCombo.selectedItem as? GroupMode ?: GroupMode.PLAIN

    init {
        border = JBUI.Borders.empty(6, 8, 2, 8)
        isOpaque = false

        val labelInsets = Insets(2, 0, 0, 4)
        val comboInsets = Insets(0, 0, 4, 0)

        var row = 0
        fun addRow(label: String, combo: ComboBox<*>) {
            add(JBLabel("$label:"), GridBagConstraints().apply {
                gridx = 0; gridy = row; anchor = GridBagConstraints.WEST; insets = labelInsets
            })
            row++
            add(combo, GridBagConstraints().apply {
                gridx = 0; gridy = row; fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0; insets = comboInsets
            })
            row++
        }

        addRow("Org", orgCombo)
        addRow("Repo", repoCombo)
        addRow("Type", typeCombo)
        addRow("State", stateCombo)
        addRow("View", groupCombo)

        orgCombo.addActionListener { if (!suppressEvents) onFilterChanged() }
        repoCombo.addActionListener { if (!suppressEvents) onFilterChanged() }
        typeCombo.addActionListener { if (!suppressEvents) onFilterChanged() }
        stateCombo.addActionListener { if (!suppressEvents) onFilterChanged() }
        groupCombo.addActionListener { if (!suppressEvents) onFilterChanged() }
    }

    fun updateOrgRepoChoices(items: List<OrbiItem>) {
        suppressEvents = true
        try {
            val orgs = items.map { it.org }.distinct().sorted()
            val repos = items.map { "${it.org}/${it.repo}" }.distinct().sorted()

            val prevOrg = orgCombo.selectedItem
            orgCombo.model = DefaultComboBoxModel((listOf("All Orgs") + orgs).toTypedArray())
            orgCombo.selectedItem = prevOrg

            val prevRepo = repoCombo.selectedItem
            repoCombo.model = DefaultComboBoxModel((listOf("All Repos") + repos).toTypedArray())
            repoCombo.selectedItem = prevRepo
        } finally {
            suppressEvents = false
        }
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
}
