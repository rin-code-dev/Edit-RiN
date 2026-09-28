package com.hikariatelier.app

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.io.File

/** Snapshot jobs and their state outlive an Activity recreation together. */
internal class WorkSnapshotViewModel(
    private val session: EditorSessionViewModel,
    private val workOperations: WorkManagementViewModel,
    private val filesDir: File
) : ViewModel() {
    private var loadedWorkId by mutableStateOf<String?>(null)
    private var snapshots by mutableStateOf<List<WorkSnapshot>>(emptyList())
    private var loadingWorkId by mutableStateOf<String?>(null)
    private var errorText by mutableStateOf<String?>(null)
    private var loadJob: Job? = null
    private var loadSequence = 0
    val currentSnapshots get() = snapshots.takeIf { loadedWorkId == session.activeWorkIdState.value }.orEmpty()
    val loading get() = loadingWorkId == session.activeWorkIdState.value
    val error get() = errorText.takeIf { loadedWorkId == session.activeWorkIdState.value }
    val notices = UiNotices()

    fun migrateLegacy() {
        val work = session.worksState.value.find { it.id == session.activeWorkIdState.value } ?: return
        val revisions = work.revisions.toList()
        if (revisions.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { WorkSnapshotStore.ensureLegacyMigrated(filesDir, work.id, revisions) }
                .onFailure { Log.w("EditKIRO", "Could not migrate legacy snapshots; preserving revisions", it) }
        }
    }

    fun load() {
        if (session.snapshotOperationWorkId != null) return
        val work = session.worksState.value.find { it.id == session.activeWorkIdState.value } ?: return
        val id = work.id
        val revisions = work.revisions.toList()
        val sequence = ++loadSequence
        loadJob?.cancel()
        if (loadedWorkId != id) snapshots = emptyList()
        loadedWorkId = id
        loadingWorkId = id
        errorText = null
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { WorkSnapshotStore.loadSnapshots(filesDir, id, revisions) }
                if (session.activeWorkIdState.value == id) snapshots = loaded
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (loadSequence == sequence) errorText = "スナップショットを読み込めませんでした。既存の記録は上書きしません。" }
            finally { if (loadSequence == sequence) loadingWorkId = null }
        }.also { loadJob = it }
    }

    private fun operate(errorMessage: String, restoring: Boolean = false, action: suspend (Work) -> Unit) {
        if (session.snapshotOperationWorkId != null || session.assetBusy || loading || error != null) return
        val work = session.worksState.value.find { it.id == session.activeWorkIdState.value } ?: return
        session.snapshotOperationWorkId = work.id
        session.snapshotRestoring = restoring
        if (restoring) session.assetBusy = true
        viewModelScope.launch {
            try { action(work) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notices.send(errorMessage) }
            finally {
                session.snapshotRestoring = false
                session.snapshotOperationWorkId = null
                if (restoring) session.assetBusy = false
            }
        }
    }

    fun create() = operate("スナップショットを保存できませんでした。編集内容は保持されています。") { work ->
        val revisions = work.revisions.toList()
        val content = currentSnapshotContent(workOperations.workForSnapshot(work), session.editorValueState.value.text, session.fileDrafts)
        val saved = withContext(Dispatchers.IO) { WorkSnapshotStore.addSnapshot(filesDir, work.id, content, revisions) }
        if (session.activeWorkIdState.value == work.id) { loadedWorkId = work.id; snapshots = saved }
        notices.sendWithHaptic("スナップショットを記録しました")
    }
    fun restore(snapshot: WorkSnapshot) = operate("復元を保存できませんでした。元の編集内容は保持されています。", restoring = true) {
        workOperations.commitSnapshotRestore(snapshot)
    }
    fun delete(snapshot: WorkSnapshot) = operate("スナップショットを削除できませんでした。") { work ->
        val revisions = work.revisions.toList()
        val remaining = withContext(Dispatchers.IO) { WorkSnapshotStore.deleteSnapshot(filesDir, work.id, snapshot.id, revisions) }
        if (session.activeWorkIdState.value == work.id) { loadedWorkId = work.id; snapshots = remaining }
    }
}
