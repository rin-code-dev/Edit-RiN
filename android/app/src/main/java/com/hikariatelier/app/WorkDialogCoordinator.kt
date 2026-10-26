package com.hikariatelier.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Identifies a dialog destination tied to a specific work and request generation.
 */
internal sealed interface WorkDialogDestination {
    val workId: String
    val generation: Long

    data class WorkSettings(
        override val workId: String,
        override val generation: Long,
        val initialTab: Int = 0
    ) : WorkDialogDestination

    data class AssetManager(
        override val workId: String,
        override val generation: Long
    ) : WorkDialogDestination

    data class AssetPreview(
        override val workId: String,
        override val generation: Long,
        val assetName: String
    ) : WorkDialogDestination

    data class ProjectFiles(
        override val workId: String,
        override val generation: Long
    ) : WorkDialogDestination

    data class AuxiliaryFileEditor(
        override val workId: String,
        override val generation: Long,
        val fileName: String
    ) : WorkDialogDestination

    data class RuntimeSettings(
        override val workId: String,
        override val generation: Long
    ) : WorkDialogDestination

    data class AspectRatio(
        override val workId: String,
        override val generation: Long
    ) : WorkDialogDestination

    data class HistoryAndSnapshots(
        override val workId: String,
        override val generation: Long,
        val initialTab: Int = 0 // 0 = all, 1 = revisions, 2 = snapshots
    ) : WorkDialogDestination

    data class Tags(
        override val workId: String,
        override val generation: Long
    ) : WorkDialogDestination
}

/**
 * Manages the backstack of work-scoped dialog destinations, ensuring proper navigation,
 * work-switch invalidation, and parent restoration.
 */
internal class WorkDialogCoordinator {
    private val backStack = mutableStateListOf<WorkDialogDestination>()

    val currentDestination: WorkDialogDestination?
        get() = backStack.lastOrNull()

    val isShowing: Boolean
        get() = backStack.isNotEmpty()

    /**
     * Clears existing stack and opens a root destination.
     */
    fun openRoot(destination: WorkDialogDestination) {
        backStack.clear()
        backStack.add(destination)
    }

    /**
     * Pushes a child destination onto the backstack.
     */
    fun push(destination: WorkDialogDestination) {
        // Prevent duplicate consecutive pushes
        if (backStack.lastOrNull() == destination) return
        backStack.add(destination)
    }

    /**
     * Navigates back. Returns true if popped, false if stack was already empty.
     */
    fun pop(): Boolean {
        if (backStack.isNotEmpty()) {
            backStack.removeAt(backStack.lastIndex)
            return true
        }
        return false
    }

    /**
     * Dismisses all work dialogs.
     */
    fun closeAll() {
        backStack.clear()
    }

    /**
     * Invalidates dialogs when the active work or editor generation changes.
     */
    fun invalidateForWorkChange(currentWorkId: String, currentGeneration: Long) {
        if (backStack.any { it.workId != currentWorkId || it.generation != currentGeneration }) {
            backStack.clear()
        }
    }

    /**
     * Checks if a destination of the given type is currently active on top of the stack.
     */
    inline fun <reified T : WorkDialogDestination> isCurrent(): Boolean =
        currentDestination is T

    /**
     * Gets the active destination cast to the requested type, or null.
     */
    inline fun <reified T : WorkDialogDestination> currentAs(): T? =
        currentDestination as? T
}
