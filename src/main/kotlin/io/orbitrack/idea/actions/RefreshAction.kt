package io.orbitrack.idea.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.orbitrack.idea.services.OrbiTrackProjectService

class RefreshAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        OrbiTrackProjectService.getInstance(project).refresh(forceFullRefresh = true)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
            && !OrbiTrackProjectService.getInstance(project).isLoading
    }
}
