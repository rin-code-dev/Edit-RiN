package com.hikariatelier.app

internal enum class WorkLoadFailure { UNAVAILABLE, CORRUPT }

/** Empty is a valid, writable store; Failed must never be replaced with sample works. */
internal sealed interface WorkLoadResult {
    data class Loaded(
        val store: WorkStore,
        val recovered: Boolean = false,
        val deferredWorkIds: Set<String> = emptySet()
    ) : WorkLoadResult
    data object Empty : WorkLoadResult
    data class Failed(val reason: WorkLoadFailure, val message: String) : WorkLoadResult
}

/** A conflict needs an explicit reload/merge, rather than an ordinary I/O retry. */
internal sealed interface WorkSaveResult {
    data object Saved : WorkSaveResult
    data object Conflict : WorkSaveResult
    data object Failed : WorkSaveResult
}

internal class WorkStoreConflictException : IllegalStateException("Saved works changed outside this session")
