package dev.anvas.orbitrack.idea.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import dev.anvas.orbitrack.idea.services.OrbiTrackProjectService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class CreateIssueAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = OrbiTrackProjectService.getInstance(project)
        val repos = service.trackedRepos.filter { it.enabled }
        if (repos.isEmpty()) {
            JOptionPane.showMessageDialog(
                null,
                "No tracked repositories found.",
                "OrbiTrack",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val dialog = CreateIssueDialog(repos.map { "${it.org}/${it.repo}" })
        if (dialog.showAndGet()) {
            val selected = dialog.selectedRepo
            val parts = selected.split("/", limit = 2)
            val org = parts[0]
            val repo = parts[1]
            val title = dialog.issueTitle
            val body = dialog.issueBody
            val labels = dialog.issueLabels
            val assignees = dialog.issueAssignees

            service.createIssue(org, repo, title, body, labels, assignees) { success, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (success) {
                        // Item was already added to the list by the service
                    } else {
                        JOptionPane.showMessageDialog(
                            null,
                            "Failed to create issue: $error",
                            "OrbiTrack",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
            && !OrbiTrackProjectService.getInstance(project).isLoading
    }
}

private class CreateIssueDialog(
    private val repoChoices: List<String>
) : DialogWrapper(true) {

    private val repoCombo = ComboBox(DefaultComboBoxModel(repoChoices.toTypedArray()))
    private val titleField = JTextField()
    private val bodyArea = JTextArea(10, 50).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val labelsField = JTextField()
    private val assigneesField = JTextField()

    val selectedRepo: String get() = repoCombo.selectedItem as String
    val issueTitle: String get() = titleField.text.trim()
    val issueBody: String get() = bodyArea.text.trim()
    val issueLabels: List<String>
        get() = labelsField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val issueAssignees: List<String>
        get() = assigneesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    init {
        title = "Create New Issue"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(8)

        val labelInsets = Insets(4, 0, 2, 8)
        val fieldInsets = Insets(0, 0, 8, 0)
        var row = 0

        fun addRow(label: String, component: JComponent) {
            panel.add(JLabel("$label:"), GridBagConstraints().apply {
                gridx = 0; gridy = row; anchor = GridBagConstraints.NORTHWEST; insets = labelInsets
            })
            panel.add(component, GridBagConstraints().apply {
                gridx = 1; gridy = row; fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0; insets = fieldInsets
            })
            row++
        }

        addRow("Repository", repoCombo)
        addRow("Title", titleField)

        // Body gets special treatment for vertical expansion
        panel.add(JLabel("Body:"), GridBagConstraints().apply {
            gridx = 0; gridy = row; anchor = GridBagConstraints.NORTHWEST; insets = labelInsets
        })
        panel.add(JScrollPane(bodyArea), GridBagConstraints().apply {
            gridx = 1; gridy = row; fill = GridBagConstraints.BOTH
            weightx = 1.0; weighty = 1.0; insets = fieldInsets
        })
        row++

        addRow("Labels", labelsField)
        addRow("Assignees", assigneesField)

        // Hint labels
        panel.add(JLabel(), GridBagConstraints().apply { gridx = 0; gridy = row })
        panel.add(JLabel("<html><small><i>Comma-separated for labels and assignees</i></small></html>"), GridBagConstraints().apply {
            gridx = 1; gridy = row; anchor = GridBagConstraints.WEST; insets = Insets(0, 0, 0, 0)
        })

        panel.preferredSize = Dimension(500, 400)
        return panel
    }

    override fun doOKAction() {
        if (issueTitle.isEmpty()) {
            JOptionPane.showMessageDialog(
                contentPane,
                "Title is required.",
                "Validation",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        super.doOKAction()
    }
}

