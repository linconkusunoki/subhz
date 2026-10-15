package com.subhz.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhz.data.preferences.ServerConfig
import com.subhz.data.preferences.ServerPreferences
import com.subhz.data.repository.SubsonicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Form state of the login / reconfigure screen. Prefilled from the stored server when
 * the user is already signed in (Settings > Change server), empty on first launch.
 */
data class LoginState(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Flips once the credentials were accepted and persisted. */
    val isSaved: Boolean = false,
) {
    val canSubmit: Boolean get() = !isLoading && url.isNotBlank() && username.isNotBlank()
}

/**
 * Drives LoginScreen: verifies the entered credentials against `ping.view` and only
 * persists them once the server accepts them.
 *
 * Nothing to navigate here — saving flips [ServerPreferences.config], which is what
 * gates the rest of the app.
 */
class LoginViewModel(
    private val repository: SubsonicRepository,
    private val serverPreferences: ServerPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(prefill())
    val uiState: StateFlow<LoginState> = _uiState.asStateFlow()

    fun onUrlChanged(value: String) = update { copy(url = value, error = null) }

    fun onUsernameChanged(value: String) = update { copy(username = value, error = null) }

    fun onPasswordChanged(value: String) = update { copy(password = value, error = null) }

    /**
     * Verifies the entered credentials against the entered server and saves them on
     * success. On failure the probe target is dropped, so a failed re-configure leaves
     * the app talking to the server it was already signed in to.
     */
    fun submit() {
        val form = _uiState.value
        if (form.isLoading) return

        val config = ServerConfig.parse(form.url, form.username, form.password)
        if (config == null) {
            _uiState.value = form.copy(error = "Enter a valid server address, e.g. music.example.com")
            return
        }
        if (form.password.isBlank()) {
            _uiState.value = form.copy(error = "Enter your password")
            return
        }

        _uiState.value = form.copy(isLoading = true, error = null)
        // In-memory only: the interceptor must talk to the address being tested, but the
        // app must stay on this screen until the server has accepted the credentials.
        serverPreferences.setProbeTarget(config)

        viewModelScope.launch {
            repository.pingServer()
                .onSuccess {
                    serverPreferences.save(config)
                    _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                }
                .onFailure { error ->
                    serverPreferences.setProbeTarget(null)
                    _uiState.value = form.copy(
                        isLoading = false,
                        error = error.message ?: "Could not reach that server",
                    )
                }
        }
    }

    private fun prefill() = serverPreferences.activeServer?.let {
        LoginState(url = it.url, username = it.username, password = it.password)
    } ?: LoginState()

    private fun update(block: LoginState.() -> LoginState) {
        _uiState.value = _uiState.value.block()
    }
}