package dev.anvas.orbitrack.idea.services

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import dev.anvas.orbitrack.idea.api.OkHttpGitHubClient

@Service(Service.Level.APP)
class OrbiTrackAppService {

    companion object {
        fun getInstance(): OrbiTrackAppService =
            ApplicationManager.getApplication().getService(OrbiTrackAppService::class.java)

        private const val SUBSYSTEM = "OrbiTrack"
        private const val KEY = "github-pat"
    }

    private val credentialAttributes = CredentialAttributes(
        generateServiceName(SUBSYSTEM, KEY)
    )

    var token: String?
        get() = PasswordSafe.instance.getPassword(credentialAttributes)
        set(value) {
            PasswordSafe.instance.set(credentialAttributes, value?.let { Credentials("", it) })
            _client = null  // invalidate cached client
        }

    private var _client: OkHttpGitHubClient? = null

    fun getClient(): OkHttpGitHubClient? {
        val t = token
        if (t.isNullOrBlank()) return null
        if (_client == null) {
            _client = OkHttpGitHubClient(t)
        }
        return _client
    }
}
