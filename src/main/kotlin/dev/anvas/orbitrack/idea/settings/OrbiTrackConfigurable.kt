package dev.anvas.orbitrack.idea.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import dev.anvas.orbitrack.idea.services.OrbiTrackAppService
import javax.swing.JComponent
import javax.swing.JPanel

class OrbiTrackConfigurable : Configurable {

    private var tokenField: JBPasswordField? = null
    private var mainPanel: JPanel? = null

    /** Cached token read from PasswordSafe during reset(), used by isModified() to avoid EDT-blocking calls. */
    private var cachedToken: String = ""

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
        val fieldValue = String(tokenField?.password ?: charArrayOf())
        return cachedToken != fieldValue
    }

    override fun apply() {
        val value = String(tokenField?.password ?: charArrayOf())
        OrbiTrackAppService.getInstance().token = value.ifBlank { null }
        cachedToken = value
    }

    override fun reset() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val current = OrbiTrackAppService.getInstance().token.orEmpty()
            cachedToken = current
            ApplicationManager.getApplication().invokeLater {
                tokenField?.text = current
            }
        }
    }

    override fun disposeUIResources() {
        tokenField = null
        mainPanel = null
    }
}
