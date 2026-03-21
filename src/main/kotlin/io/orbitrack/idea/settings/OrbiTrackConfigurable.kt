package io.orbitrack.idea.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import io.orbitrack.idea.services.OrbiTrackAppService
import javax.swing.JComponent
import javax.swing.JPanel

class OrbiTrackConfigurable : Configurable {

    private var tokenField: JBPasswordField? = null
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "OrbiTrack"

    override fun createComponent(): JComponent {
        tokenField = JBPasswordField()

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("GitHub Personal Access Token:"), tokenField!!, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel.apply {
                border = JBUI.Borders.empty(10)
            }

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val currentToken = OrbiTrackAppService.getInstance().token.orEmpty()
        val fieldValue = String(tokenField?.password ?: charArrayOf())
        return currentToken != fieldValue
    }

    override fun apply() {
        val value = String(tokenField?.password ?: charArrayOf())
        OrbiTrackAppService.getInstance().token = value.ifBlank { null }
    }

    override fun reset() {
        val current = OrbiTrackAppService.getInstance().token.orEmpty()
        tokenField?.text = current
    }

    override fun disposeUIResources() {
        tokenField = null
        mainPanel = null
    }
}
