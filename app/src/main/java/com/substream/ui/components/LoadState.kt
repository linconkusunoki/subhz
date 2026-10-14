package com.substream.ui.components

/**
 * Generic async list state shared by the screens that load a list from the server: idle while
 * nothing has been asked for, loading, the items, or why it failed.
 */
sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Success<T>(val items: List<T>) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}
