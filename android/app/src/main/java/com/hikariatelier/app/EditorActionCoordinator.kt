package com.hikariatelier.app

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Result of an action coordinated through [EditorActionCoordinator].
 */
internal sealed interface CoordinatorResult<out T> {
    data class Success<T>(val value: T) : CoordinatorResult<T>
    data class Rejected(val reason: String) : CoordinatorResult<Nothing>
    data class Cancelled(val reason: String = "Cancelled") : CoordinatorResult<Nothing>
    object Superseded : CoordinatorResult<Nothing>
    data class Failed(val message: String, val cause: Throwable? = null) : CoordinatorResult<Nothing>

    val isSuccess: Boolean get() = this is Success
}

/**
 * Categorizes the nature of an editor action to enforce proper flush and permission rules.
 */
internal enum class ActionKind {
    SAVE,
    SWITCH_WORK,
    SWITCH_FILE,
    SWITCH_BLOCK_MODE,
    SWITCH_SPLIT_LAYOUT,
    UNDO,
    REDO,
    FORMAT,
    SNIPPET,
    SEARCH_REPLACE,
    PROJECT_SETTINGS,
    RUNTIME_SETTINGS,
    SNAPSHOT_CREATE,
    SNAPSHOT_RESTORE,
    REVISION_RESTORE,
    RUN_SKETCH,
    RUN_FULL_PREVIEW,
    SHORTENED_PREVIEW,
    EXPORT,
    IMPORT,
    GENERAL
}

/**
 * Snapshot of validated editor state passed to action blocks once前提 conditions are satisfied.
 */
internal data class ActionContext(
    val workId: String,
    val activationGeneration: Long,
    val folderUri: Uri?,
    val isSample: Boolean,
    val currentCode: String,
    val blockEditorState: String?,
    val revisionToken: String
)

/**
 * Central coordinator for UI-initiated actions requiring precondition validation,
 * block-editor flushes, ownership consistency, and typed outcome reporting.
 */
internal class EditorActionCoordinator(
    private val session: EditorSessionViewModel,
    private val scope: CoroutineScope,
    private val folderUriProvider: () -> Uri?,
    private val onSampleCopyRequested: () -> Unit,
    private val onFlushFailure: () -> Unit = {}
) {
    private val actionMutex = Mutex()

    /**
     * Executes an action requiring block flush and ownership check.
     *
     * @param kind Semantic category of the action.
     * @param targetWorkId Target work ID (defaults to active work).
     * @param requireEditable When true, read-only samples are diverted to copy-request and rejected here.
     * @param requireFlush When true, awaits pending block editor edits before proceeding.
     * @param hideChaff When flushing, whether to dismiss popups and intermediate fields in Blockly.
     * @param action The operation to perform once preconditions and flushes are satisfied.
     */
    suspend fun <T> executeAction(
        kind: ActionKind,
        targetWorkId: String = session.activeWorkIdState.value,
        requireEditable: Boolean = false,
        requireFlush: Boolean = true,
        hideChaff: Boolean = true,
        action: suspend (ActionContext) -> CoordinatorResult<T>
    ): CoordinatorResult<T> {
        val initialFolder = folderUriProvider()
        val initialActivation = session.blockEditorActivationGeneration
        return actionMutex.withLock {
            // A queued request belongs to the activation that received the click.
            if (session.activeWorkIdState.value != targetWorkId ||
                session.blockEditorActivationGeneration != initialActivation || folderUriProvider() != initialFolder) {
                return@withLock CoordinatorResult.Superseded
            }

            // Precondition 1: Check if work exists
            val work = session.worksState.value.firstOrNull { it.id == targetWorkId }
                ?: return@withLock CoordinatorResult.Rejected("Work no longer exists.")
            val isSample = work.isSample || session.sampleReadOnly

            if (requireEditable && isSample) {
                onSampleCopyRequested()
                return@withLock CoordinatorResult.Rejected("Sample is read-only. Copy requested.")
            }

            // Precondition 2: Check lock state
            if (session.editorInputLocked && kind != ActionKind.SAVE && kind != ActionKind.SWITCH_WORK) {
                return@withLock CoordinatorResult.Rejected("Editor input is currently locked.")
            }

            // Flush block editor if requested and block mode is active
            if (requireFlush && session.blockEditorFlusher != null && session.blockModeState(targetWorkId).value) {
                val flushSucceeded = session.flushBlockEditor(hideChaff = hideChaff)
                if (!flushSucceeded) {
                    if (session.activeWorkIdState.value != targetWorkId ||
                        session.blockEditorActivationGeneration != initialActivation || folderUriProvider() != initialFolder) {
                        return@withLock CoordinatorResult.Superseded
                    }
                    return@withLock CoordinatorResult.Failed("Block editor flush timed out or failed.")
                }
            }

            // Ownership and superseded checks after suspension point
            if (session.activeWorkIdState.value != targetWorkId) {
                return@withLock CoordinatorResult.Superseded
            }
            if (session.blockEditorActivationGeneration != initialActivation) {
                return@withLock CoordinatorResult.Superseded
            }
            if (folderUriProvider() != initialFolder) {
                return@withLock CoordinatorResult.Superseded
            }
            if (session.worksState.value.none { it.id == targetWorkId } ||
                (requireEditable && session.sampleReadOnly) ||
                (session.editorInputLocked && kind != ActionKind.SAVE && kind != ActionKind.SWITCH_WORK)) {
                return@withLock CoordinatorResult.Superseded
            }

            val effectiveCode = session.editorValueState.value.text
            val blockState = session.currentBlockEditorState(targetWorkId)
            val revision = session.currentBlockDocumentRevision(targetWorkId)

            val context = ActionContext(
                workId = targetWorkId,
                activationGeneration = initialActivation,
                folderUri = initialFolder,
                isSample = isSample,
                currentCode = effectiveCode,
                blockEditorState = blockState,
                revisionToken = revision
            )

            try {
                action(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                CoordinatorResult.Failed(e.message ?: "Action failed", e)
            }
        }
    }

    /**
     * Launch an action asynchronously in the coordinator scope.
     */
    fun <T> launchAction(
        kind: ActionKind,
        targetWorkId: String = session.activeWorkIdState.value,
        requireEditable: Boolean = false,
        requireFlush: Boolean = true,
        hideChaff: Boolean = true,
        onResult: ((CoordinatorResult<T>) -> Unit)? = null,
        action: suspend (ActionContext) -> CoordinatorResult<T>
    ): Job {
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val result = executeAction(
                kind = kind,
                targetWorkId = targetWorkId,
                requireEditable = requireEditable,
                requireFlush = requireFlush,
                hideChaff = hideChaff,
                action = action
            )
            if (result is CoordinatorResult.Failed && result.message == "Block editor flush timed out or failed.") {
                onFlushFailure()
            }
            onResult?.invoke(result)
        }
    }
}
