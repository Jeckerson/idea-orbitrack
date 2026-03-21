package io.orbitrack.idea.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.orbitrack.idea.model.ItemType
import io.orbitrack.idea.model.OrbiItem
import io.orbitrack.idea.services.OrbiTrackProjectService
import java.awt.BorderLayout
import javax.swing.*

class OrbiTrackPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val service = OrbiTrackProjectService.getInstance(project)

    private val listModel = DefaultListModel<ListEntry>()
    private val itemList = JBList(listModel).apply {
        cellRenderer = ItemCellRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        emptyText.text = "Loading\u2026"
    }

    private val filterPanel = FilterPanel(onFilterChanged = ::applyFilters)
    private val detailPanel = ItemDetailPanel().apply {
        onAddComment = { item, body ->
            service.postComment(item, body) { success, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (!success && error != null) {
                        JOptionPane.showMessageDialog(
                            this@OrbiTrackPanel,
                            "Failed to post comment: $error",
                            "OrbiTrack",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        }
        onEditComment = { item, commentId, body ->
            service.editComment(item, commentId, body) { success, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (!success && error != null) {
                        JOptionPane.showMessageDialog(
                            this@OrbiTrackPanel,
                            "Failed to edit comment: $error",
                            "OrbiTrack",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        }
        onDeleteComment = { item, commentId ->
            service.deleteComment(item, commentId) { success, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (!success && error != null) {
                        JOptionPane.showMessageDialog(
                            this@OrbiTrackPanel,
                            "Failed to delete comment: $error",
                            "OrbiTrack",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        }
        onMergePR = { item, mergeMethod ->
            service.mergePull(item, mergeMethod) { success, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (success) {
                        JOptionPane.showMessageDialog(
                            this@OrbiTrackPanel,
                            "PR #${item.number} merged successfully!",
                            "OrbiTrack",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    } else {
                        JOptionPane.showMessageDialog(
                            this@OrbiTrackPanel,
                            "Failed to merge PR: ${error ?: "Unknown error"}",
                            "OrbiTrack",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        }
        onCheckoutBranch = { item ->
            service.checkoutBranch(item) { success, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (success) {
                        JOptionPane.showMessageDialog(
                            this@OrbiTrackPanel,
                            "Checked out branch '${item.headBranch}' successfully!",
                            "OrbiTrack",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    } else {
                        JOptionPane.showMessageDialog(
                            this@OrbiTrackPanel,
                            "Failed to checkout branch: ${error ?: "Unknown error"}",
                            "OrbiTrack",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        }
        onRefreshItem = { item ->
            service.refreshItem(item) { success, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (!success) {
                        this.hideLoading()
                        if (error != null) {
                            JOptionPane.showMessageDialog(
                                this@OrbiTrackPanel,
                                "Failed to refresh: $error",
                                "OrbiTrack",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }
            }
        }
    }

    private val loadMoreButton = JButton("Load more\u2026").apply {
        addActionListener { service.fetchMore() }
    }
    private val loadMorePanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(4)
        add(loadMoreButton, BorderLayout.CENTER)
    }

    private val dataListener = OrbiTrackProjectService.DataListener {
        ApplicationManager.getApplication().invokeLater { onDataChanged() }
    }

    init {
        // --- toolbar ---
        val actionGroup = DefaultActionGroup().apply {
            ActionManager.getInstance().getAction("OrbiTrack.Refresh")?.let(::add)
            ActionManager.getInstance().getAction("OrbiTrack.CreateIssue")?.let(::add)
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, actionGroup, true)
        toolbar.targetComponent = this
        val toolbarPanel = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.WEST)
        }

        // --- left panel (filters + list + load more) ---
        val leftPanel = JPanel(BorderLayout()).apply {
            add(filterPanel, BorderLayout.NORTH)
            add(JBScrollPane(itemList).apply {
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            }, BorderLayout.CENTER)
            add(loadMorePanel, BorderLayout.SOUTH)
        }

        // --- splitter ---
        val splitter = JBSplitter(false, 0.35f).apply {
            firstComponent = leftPanel
            secondComponent = detailPanel
            border = JBUI.Borders.empty()
        }

        add(toolbarPanel, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)

        // --- selection listener ---
        itemList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val sel = itemList.selectedValue
                if (sel is ListEntry.Item) {
                    detailPanel.showItem(
                        sel.item,
                        service.getComments(sel.item.number),
                        service.getTimeline(sel.item.number)
                    )
                    service.loadComments(sel.item)
                    service.loadTimeline(sel.item)
                    service.loadPullDetail(sel.item)
                } else {
                    detailPanel.showEmpty()
                }
            }
        }

        // --- subscribe to service ---
        service.addDataListener(dataListener)

        // --- detect repos and start initial fetch ---
        service.detectRepos()
        service.refresh()
    }

    private fun onDataChanged() {
        val svc = service

        if (svc.isLoading) {
            itemList.emptyText.text = "Loading\u2026"
            loadMoreButton.isEnabled = false
            loadMoreButton.text = "Loading\u2026"
        } else if (svc.lastError != null) {
            itemList.emptyText.text = svc.lastError!!
            loadMoreButton.isEnabled = false
            loadMoreButton.text = "Load more\u2026"
        } else {
            itemList.emptyText.text = "No issues or PRs found"
            loadMoreButton.isEnabled = svc.hasMore
            loadMoreButton.text = if (svc.hasMore) "Load more\u2026" else "All items loaded"
        }

        // Show/hide load more
        loadMorePanel.isVisible = !svc.isLoading || svc.items.isNotEmpty()

        // Update list
        val items = svc.items
        filterPanel.updateOrgRepoChoices(items)
        applyFilters()

        // Refresh detail if selected item's comments/timeline/PR detail were loaded
        val sel = itemList.selectedValue
        if (sel is ListEntry.Item) {
            // Get the freshest version of this item (may have been enriched with PR detail)
            val freshItem = svc.items.find {
                it.org == sel.item.org && it.repo == sel.item.repo && it.number == sel.item.number
            } ?: sel.item
            detailPanel.showItem(
                freshItem,
                svc.getComments(freshItem.number),
                svc.getTimeline(freshItem.number)
            )
        }
    }

    private fun applyFilters() {
        val filtered = filterPanel.applyFilter(service.items)
        val sorted = filterPanel.applySort(filtered)
        val selectedItem = (itemList.selectedValue as? ListEntry.Item)?.item

        val entries = buildGroupedEntries(sorted, filterPanel.groupModes)

        listModel.clear()
        entries.forEach { listModel.addElement(it) }

        // Restore selection by identity (org/repo/number), not full equality
        if (selectedItem != null) {
            for (i in 0 until listModel.size()) {
                val entry = listModel.get(i)
                if (entry is ListEntry.Item
                    && entry.item.org == selectedItem.org
                    && entry.item.repo == selectedItem.repo
                    && entry.item.number == selectedItem.number
                ) {
                    itemList.selectedIndex = i
                    break
                }
            }
        }
    }

    /**
     * Recursively groups items by the ordered list of [modes].
     * Empty modes list = flat list (no grouping).
     */
    private fun buildGroupedEntries(items: List<OrbiItem>, modes: List<GroupMode>): List<ListEntry> {
        if (modes.isEmpty() || items.isEmpty()) {
            return items.map { ListEntry.Item(it) }
        }
        return buildGrouped(items, modes, depth = 0)
    }

    private fun buildGrouped(items: List<OrbiItem>, modes: List<GroupMode>, depth: Int): List<ListEntry> {
        if (modes.isEmpty()) {
            return items.map { ListEntry.Item(it, depth) }
        }

        val mode = modes.first()
        val rest = modes.drop(1)

        val grouped: Map<String, List<OrbiItem>> = when (mode) {
            GroupMode.BY_ORG -> items.groupBy { it.org }
            GroupMode.BY_REPO -> items.groupBy { "${it.org}/${it.repo}" }
            GroupMode.BY_TYPE -> items.groupBy { if (it.type == ItemType.PR) "Pull Requests" else "Issues" }
        }

        val result = mutableListOf<ListEntry>()
        for ((key, group) in grouped.toSortedMap()) {
            result.add(ListEntry.Header("$key (${group.size})", depth))
            result.addAll(buildGrouped(group, rest, depth + 1))
        }
        return result
    }

    override fun dispose() {
        service.removeDataListener(dataListener)
    }
}
