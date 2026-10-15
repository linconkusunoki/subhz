package com.subhz.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhz.data.api.SubsonicPingData
import com.subhz.data.repository.SubsonicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// =====================================================================
// 1. THE STATE (UI Representation)
// =====================================================================

/**
 * Represents the distinct states the Ping Screen can be in.
 * Similar to a Union Type in TypeScript.
 */
sealed class PingState {
    object Idle : PingState()
    object Loading : PingState()
    data class Success(val data: SubsonicPingData) : PingState()
    data class Error(val message: String) : PingState()
}

// =====================================================================
// 2. THE VIEWMODEL (Logic & State Management)
// =====================================================================

class PingViewModel(
    private val repository: SubsonicRepository
) : ViewModel() {

    // Internal mutable state (Only the ViewModel can modify this)
    private val _uiState = MutableStateFlow<PingState>(PingState.Idle)

    // External immutable state (The UI component collects this)
    val uiState: StateFlow<PingState> = _uiState.asStateFlow()

    /**
     * Triggers the ping action against the server the user is signed in to.
     */
    fun testConnection() {
        // Update state to Loading immediately
        _uiState.value = PingState.Loading

        // viewModelScope ensures the Coroutine is tied to the ViewModel's lifecycle.
        // If the screen is destroyed, the network request is automatically canceled.
        viewModelScope.launch {
            val result = repository.pingServer()

            result.fold(
                onSuccess = { pingData ->
                    _uiState.value = PingState.Success(pingData)
                },
                onFailure = { error ->
                    _uiState.value = PingState.Error(error.message ?: "An unknown error occurred")
                }
            )
        }
    }
}