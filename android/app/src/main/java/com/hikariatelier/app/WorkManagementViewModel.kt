package com.hikariatelier.app

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.json.JSONObject

internal data class WorkEvent(
    val rerun: Boolean = false,
    val forceRun: Boolean = false,
    val closeAuxiliary: Boolean = false,
    val closeHistory: Boolean = false,
    val message: String? = null,
    val failure: Boolean = false,
    val openSettings: Boolean = false,
    val openFolder: Boolean = false,
    val clearError: Boolean = false,
    val haptic: Boolean = false,
    val folderChanged: Boolean = false
)

/** Owns work commands and publishes state only after durable persistence succeeds. */
internal class WorkManagementViewModel(
    private val session: EditorSessionViewModel,
    val persistence: WorkPersistence,
    private val drafts: DraftSnapshotRepository? = null,
    private val transfer: WorkTransferRepository? = null,
    private val settings: SettingsViewModel? = null,
    private val folders: WorkFolderRepository? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val operationScope: CoroutineScope? = null
) : ViewModel() {
    var workMenuExpanded by mutableStateOf(false)
    var workSettingsMenuExpanded by mutableStateOf(false)
    var workActionsMenuExpanded by mutableStateOf(false)
    var showSamplePrompt by mutableStateOf(true)
    var showAddDialog by mutableStateOf(false)
    var showDeleteDialog by mutableStateOf(false)
    var showRenameDialog by mutableStateOf(false)
    var showProjectFilesDialog by mutableStateOf(false)
    var showAuxiliaryFileEditor by mutableStateOf(false)
    var auxiliaryFileName by mutableStateOf("")
    var originalAuxiliaryFileName by mutableStateOf<String?>(null)
    var auxiliaryFileContent by mutableStateOf("")
    var showHistoryDialog by mutableStateOf(false)
    var showSnapshotSheet by mutableStateOf(false)
    var showAspectRatioDialog by mutableStateOf(false)
    var showRuntimeDialog by mutableStateOf(false)
    var editingTagsWorkId by mutableStateOf<String?>(null)
    val editingTagsWork get() = session.worksState.value.find { it.id == editingTagsWorkId }
    var previewRevision by mutableIntStateOf(0)
    var updatedPreviewId by mutableStateOf<String?>(null)
    var workSaving by mutableStateOf(false)
        private set
    var showBlockingProgress by mutableStateOf(false)
        private set
    var selectedFolderUri by mutableStateOf<Uri?>(null)
        private set
    var showP5Import by mutableStateOf(false)
    var p5Sketches by mutableStateOf<List<P5Sketch>>(emptyList())
        private set
    var p5Busy by mutableStateOf(false)
        private set
    var p5Error by mutableStateOf<String?>(null)
    private val parameterDrafts = mutableStateMapOf<String, Map<String, String>>()
    private val ratioDrafts = mutableStateMapOf<String, String>()
    private val parameterJobs = mutableMapOf<String, Job>()
    private var commandJob: Job? = null
    private val eventChannel = Channel<WorkEvent>(Channel.UNLIMITED)
    val events = eventChannel.receiveAsFlow()
    private val scope get() = operationScope ?: viewModelScope
    var officialSamples by mutableStateOf<List<Work>>(emptyList())
        private set

    init { settings?.onDraftRecoveryDisabled = { drafts?.clear() } }


    fun notifyPreviewUpdated(workId: String) { updatedPreviewId = workId; previewRevision++ }

    suspend fun initialize(defaultWorks: () -> List<Work>) {
        if (session.initialized) return
        val samples = withContext(io) { defaultWorks() }
        officialSamples = samples
        val loaded = withContext(io) {
            val folder = folders?.validUri()
            val store = if (folder != null) persistence.loadFolder(folder) else persistence.loadLocal()
            val works = store?.works?.takeIf { it.isNotEmpty() } ?: samples
            val draft = if (settings?.draftRecovery != false) drafts?.load() else null
            val selectedId = persistence.selectedWorkId(folder)?.takeIf { id -> works.any { it.id == id } }
                ?: store?.activeWorkId.orEmpty()
            Triple(folder, WorkStore(works, selectedId), draft)
        }
        selectedFolderUri = loaded.first
        val works = loaded.second.works
        val activeId = loaded.second.activeWorkId.takeIf { id -> works.any { it.id == id } }
            ?: works.firstOrNull()?.id.orEmpty()
        val migrated = works.any { it.id == "gravity" && it.p5Version == P5_VERSION_CURRENT }
        works.filter { it.id == "gravity" }.forEach { it.p5Version = P5_VERSION_LEGACY }
        if (migrated && selectedFolderUri != null) withContext(io) {
            persistence.save(selectedFolderUri, works, activeId)
        }
        loaded.third?.second?.let(session.fileDrafts::putAll)
        val recovered = loaded.third?.first?.takeIf { it.workId == activeId }?.code
        session.initialize(works, activeId, recovered)
    }

    fun saveDraft() {
        if (settings?.draftRecovery != false && session.initialized) {
            drafts?.save(session.activeWorkIdState.value, session.editorValueState.value.text, session.fileDrafts.toMap())
        }
    }
    fun clearDraft() {
        if (settings?.draftRecovery != false &&
            (session.fileDrafts.isNotEmpty() || session.editorValueState.value.text != session.lastSavedTextState.value)) {
            saveDraft()
        } else drafts?.clear()
    }

    private fun emit(event: WorkEvent) { eventChannel.trySend(event) }
    private fun execute(errorText: String = "保存できませんでした。保存先を確認して再試行してください",
                        blockUi: Boolean = true, progressDelayMillis: Long = 0,
                        action: suspend () -> Unit): Job? {
        if (workSaving || session.assetBusy || session.snapshotOperationWorkId != null) return null
        workSaving = true
        showBlockingProgress = blockUi && progressDelayMillis == 0L
        session.assetBusy = true
        return scope.launch {
            val progress = if (blockUi && progressDelayMillis > 0) launch {
                delay(progressDelayMillis)
                showBlockingProgress = true
            } else null
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { emit(WorkEvent(message = errorText, failure = true)) }
            finally {
                progress?.cancel()
                workSaving = false; showBlockingProgress = false; session.assetBusy = false
            }
        }.also { commandJob = it }
    }

    private fun snapshots(includeEdits: Boolean = false): List<Work> {
        val selected = session.activeWorkIdState.value
        val code = session.editorValueState.value.text
        return session.worksState.value.map { original ->
            snapshotWork(original).also { copy ->
                if (includeEdits) {
                    parameterDrafts[copy.id]?.let { copy.parameterValues.clear(); copy.parameterValues.putAll(it) }
                    if (copy.id == selected && copy.code != code) {
                        copy.code = code; copy.updatedAt = System.currentTimeMillis()
                    }
                    copy.files.keys.toList().forEach { name ->
                        session.fileDrafts["${copy.id}/$name"]?.let { copy.files[name] = it }
                    }
                }
            }
        }
    }

    private suspend fun persist(works: List<Work>, activeId: String) {
        val folder = selectedFolderUri
        check(withContext(io) { persistence.save(folder, works, activeId) })
    }

    private fun commit(works: List<Work>, activeId: String = session.activeWorkIdState.value,
                       replaceEditor: Boolean = false, savedEdits: Boolean = false) {
        session.worksState.value = works
        if (replaceEditor) {
            session.activeWorkIdState.value = activeId
            val active = works.first { it.id == activeId }
            session.editorValueState.value = TextFieldValue(active.code)
            session.lastSavedTextState.value = active.code
            session.historyWorkId = activeId
            session.clearEditHistory()
        } else if (savedEdits) {
            session.lastSavedTextState.value = works.first { it.id == session.activeWorkIdState.value }.code
        }
        if (savedEdits) works.forEach { work ->
            if (parameterDrafts[work.id] == work.parameterValues.toMap()) parameterDrafts.remove(work.id)
            work.files.forEach { (name, code) ->
                val key = "${work.id}/$name"
                if (session.fileDrafts[key] == code) session.fileDrafts.remove(key)
            }
        }
        clearDraft()
    }

    fun saveCurrentWork(then: WorkEvent = WorkEvent(), blockUi: Boolean = false, progressDelayMillis: Long = 400) =
        execute(blockUi = blockUi, progressDelayMillis = progressDelayMillis) {
        val id = session.activeWorkIdState.value
        val original = session.worksState.value.first { it.id == id }
        val next = snapshots(includeEdits = true)
        val replacement = next.first { it.id == id }
        if (session.lastSavedTextState.value != replacement.code) {
            replacement.revisions.add(WorkRevision(session.lastSavedTextState.value, original.updatedAt))
            while (replacement.revisions.size > 30) replacement.revisions.removeAt(0)
        }
        replacement.updatedAt = System.currentTimeMillis()
        persist(next, id)
        commit(next, savedEdits = true)
        emit(then.copy(clearError = true))
    }

    private fun hasEditsToSave(): Boolean {
        val works = session.worksState.value
        val active = works.firstOrNull { it.id == session.activeWorkIdState.value }
        if (active != null && session.editorValueState.value.text != active.code) return true
        return works.any { work ->
            parameterDrafts[work.id]?.let { it != work.parameterValues.toMap() } == true ||
                work.files.any { (name, code) ->
                    session.fileDrafts["${work.id}/$name"]?.let { it != code } == true
                }
        }
    }

    fun selectWork(workId: String, openMenu: Boolean) = execute(progressDelayMillis = 350) {
        val oldId = session.activeWorkIdState.value
        if (workId != oldId) {
            require(session.worksState.value.any { it.id == workId })
            // Clean navigation does not serialize/rewrite all works or replace their objects.
            // If typing continues during an edit save, persist the newer edits before switching.
            while (hasEditsToSave()) {
                val next = snapshots(includeEdits = true)
                persist(next, workId)
                commit(next, savedEdits = true)
            }
            persistence.rememberSelectedWork(selectedFolderUri, workId)
            commit(session.worksState.value, workId, replaceEditor = true)
        }
        workMenuExpanded = false
        workActionsMenuExpanded = openMenu
        emit(WorkEvent(rerun = workId != oldId))
    }

    fun createWork(title: String, ratio: String, sizingMode: CanvasSizingMode, template: WorkTemplate) = execute {
        val work = Work(id = java.util.UUID.randomUUID().toString(), title = title,
            code = template.code(sizingMode), previewAspectRatio = ratio)
        val next = snapshots(includeEdits = true) + work
        persist(next, work.id)
        commit(next, work.id, replaceEditor = true, savedEdits = true)
        showAddDialog = false
        emit(WorkEvent(rerun = true))
    }

    fun duplicateWork() = execute {
        val current = snapshots(includeEdits = true).first { it.id == session.activeWorkIdState.value }
        val duplicate = Work(id = java.util.UUID.randomUUID().toString(), title = "${current.title} copy",
            code = current.code, files = current.files.toMutableMap(), assets = current.assets.toMap(),
            previewAspectRatio = current.previewAspectRatio, p5Version = current.p5Version,
            p5SoundEnabled = current.p5SoundEnabled, libraries = current.libraries.toMap(),
            parameterValues = current.parameterValues.toMap(), isPinned = current.isPinned, tags = current.tags.toList())
        val next = snapshots(includeEdits = true) + duplicate
        persist(next, duplicate.id)
        commit(next, duplicate.id, replaceEditor = true, savedEdits = true)
        emit(WorkEvent(rerun = true))
    }

    private fun changeWork(workId: String, event: WorkEvent = WorkEvent(), change: (Work) -> Unit) = execute {
        val next = snapshots()
        val target = next.first { it.id == workId }
        change(target)
        target.updatedAt = System.currentTimeMillis()
        persist(next, session.activeWorkIdState.value)
        commit(next)
        emit(event)
    }
    fun renameWork(workId: String, title: String) = execute {
        val next = snapshots()
        next.first { it.id == workId }.apply { this.title = title; updatedAt = System.currentTimeMillis() }
        persist(next, session.activeWorkIdState.value)
        commit(next)
        showRenameDialog = false
        emit(WorkEvent())
    }
    fun togglePin(workId: String) = changeWork(workId) { it.isPinned = !it.isPinned }
    fun addTag(workId: String, tag: String) = changeWork(workId) { work ->
        if (work.tags.none { it.equals(tag, true) }) work.tags.add(tag)
    }
    fun removeTag(workId: String, tag: String) = changeWork(workId) { work -> work.tags.removeAll { it.equals(tag, true) } }
    fun deleteGlobalTag(tag: String) = execute {
        val next = snapshots()
        next.forEach { work ->
            if (work.tags.removeAll { it.equals(tag, true) }) work.updatedAt = System.currentTimeMillis()
        }
        persist(next, session.activeWorkIdState.value)
        commit(next)
        emit(WorkEvent())
    }
    fun deleteCurrentWork() = execute {
        val id = session.activeWorkIdState.value
        val next = snapshots().filter { it.id != id }
        require(next.isNotEmpty())
        persist(next, next.first().id)
        session.clearAuxiliaryEditors(id)
        parameterDrafts.remove(id); ratioDrafts.remove(id); parameterJobs.remove(id)?.cancel()
        commit(next, next.first().id, replaceEditor = true)
        showDeleteDialog = false
        emit(WorkEvent(rerun = true))
    }
    fun addSamples(samples: List<Work>, fromPrompt: Boolean = false) = execute {
        val next = snapshots() + samples.map(::snapshotWork)
        persist(next, session.activeWorkIdState.value)
        commit(next)
        if (fromPrompt) dismissSamplePrompt()
        emit(WorkEvent(message = "サンプル作品を追加しました"))
    }
    fun dismissSamplePrompt() {
        settings?.dismissSamplePrompt(BuildConfig.VERSION_CODE)
        showSamplePrompt = false
    }
    suspend fun folderName(uri: Uri): String? = withContext(io) { folders?.name(uri) }

    private fun clearMetadataDrafts(workId: String? = null) {
        if (workId == null) {
            parameterJobs.values.forEach { it.cancel() }
            parameterJobs.clear(); parameterDrafts.clear(); ratioDrafts.clear()
        } else {
            parameterJobs.remove(workId)?.cancel(); parameterDrafts.remove(workId); ratioDrafts.remove(workId)
        }
    }
    internal fun workForSnapshot(work: Work): Work = snapshotWork(work).also { copy ->
        copy.parameterValues.clear(); copy.parameterValues.putAll(parameterValues(work))
    }
    fun restoreCurrentWork() = execute {
        val id = session.activeWorkIdState.value
        val folder = selectedFolderUri
        val store = withContext(io) { if (folder != null) persistence.loadFolder(folder) else persistence.loadLocal() }
        val restored = store?.works?.find { it.id == id } ?: error("Missing saved work")
        val next = snapshots().map { if (it.id == id) restored else it }
        session.clearAuxiliaryEditors(id)
        clearMetadataDrafts(id)
        commit(next, id, replaceEditor = true)
        emit(WorkEvent(rerun = true))
    }
    fun restoreRevision(revision: WorkRevision) = execute {
        val id = session.activeWorkIdState.value
        val next = snapshots()
        val restored = next.first { it.id == id }
        restored.revisions.add(WorkRevision(session.editorValueState.value.text, System.currentTimeMillis()))
        while (restored.revisions.size > 30) restored.revisions.removeAt(0)
        restored.code = revision.code
        restored.updatedAt = System.currentTimeMillis()
        persist(next, id)
        commit(next, id, replaceEditor = true)
        showHistoryDialog = false
        emit(WorkEvent(rerun = true, closeHistory = true))
    }
    fun changeAssets(workId: String, updated: Map<String, ProjectAsset>) = execute("素材を保存できませんでした") {
        validateAssetSet(updated)
        val next = snapshots()
        val target = next.first { it.id == workId }
        target.assets.clear(); target.assets.putAll(updated)
        target.updatedAt = System.currentTimeMillis()
        persist(next, session.activeWorkIdState.value)
        commit(next)
        if (session.activeWorkIdState.value == workId) session.assetPreviewRevision++
    }

    fun addAssets(workId: String, uris: List<Uri>, preview: PreviewAssets) =
        execute("素材を追加できませんでした。ファイル名・サイズ・保存先を確認してください") {
            val next = snapshots()
            val target = next.first { it.id == workId }
            withContext(io) { (persistence as? WorkStoreRepository)?.pruneUnusedAssets(next, preview) }
            val updated = requireNotNull(transfer).readAssets(uris, target.assets.toMap())
            target.assets.clear(); target.assets.putAll(updated)
            target.updatedAt = System.currentTimeMillis()
            persist(next, session.activeWorkIdState.value)
            commit(next)
            if (session.activeWorkIdState.value == workId) session.assetPreviewRevision++
        }

    internal suspend fun commitSnapshotRestore(snapshot: WorkSnapshot) {
        val id = session.activeWorkIdState.value
        val original = session.worksState.value.first { it.id == id }
        val current = currentSnapshotContent(workForSnapshot(original), session.editorValueState.value.text, session.fileDrafts)
        val restored = restoredSnapshotWork(original, snapshot, current)
        val next = snapshots().map { if (it.id == id) restored else it }
        persist(next, id)
        session.clearAuxiliaryEditors(id)
        clearMetadataDrafts(id)
        commit(next, id, replaceEditor = true)
        showSnapshotSheet = false
        emit(WorkEvent(forceRun = true, haptic = true, message = "スナップショットに復元しました"))
    }

    fun saveAuxiliaryFiles(updatedFiles: Map<String, String>) = execute {
        val id = session.activeWorkIdState.value
        val original = session.worksState.value.first { it.id == id }
        val changedNames = (original.files.keys + updatedFiles.keys).filter { original.files[it] != updatedFiles[it] }
        val next = snapshots()
        val replacement = next.first { it.id == id }
        replacement.code = session.editorValueState.value.text
        replacement.files.clear(); replacement.files.putAll(updatedFiles)
        replacement.updatedAt = System.currentTimeMillis()
        persist(next, id)
        changedNames.forEach { session.clearAuxiliaryEditor(id, it) }
        commit(next, savedEdits = true)
        showAuxiliaryFileEditor = false
        auxiliaryFileContent = ""
        emit(WorkEvent(forceRun = true, closeAuxiliary = true))
    }
    fun saveRuntime(version: String, sound: Boolean, libraries: Map<String, String>) = execute {
        val next = snapshots()
        val id = session.activeWorkIdState.value
        next.first { it.id == id }.apply {
            p5Version = version; p5SoundEnabled = sound; this.libraries = libraries
            updatedAt = System.currentTimeMillis()
        }
        persist(next, id); commit(next)
        showRuntimeDialog = false
        emit(WorkEvent(forceRun = true))
    }
    fun previewRatio(work: Work?): String = normalizedPreviewAspectRatio(work?.let { ratioDrafts[it.id] ?: it.previewAspectRatio })
    fun draftPreviewRatio(value: String) { ratioDrafts[session.activeWorkIdState.value] = normalizedPreviewAspectRatio(value) }
    fun commitPreviewRatio() = savePreviewRatio(previewRatio(session.worksState.value.find { it.id == session.activeWorkIdState.value }))
    fun savePreviewRatio(value: String) = execute {
        val id = session.activeWorkIdState.value
        val normalized = normalizedPreviewAspectRatio(value)
        val next = snapshots()
        next.first { it.id == id }.apply { previewAspectRatio = normalized; updatedAt = System.currentTimeMillis() }
        try { persist(next, id); commit(next) }
        finally { ratioDrafts.remove(id) }
    }

    fun hasPendingMetadata(workId: String): Boolean {
        val work = session.worksState.value.find { it.id == workId } ?: return false
        return parameterDrafts[workId]?.let { it != work.parameterValues.toMap() } == true ||
            ratioDrafts[workId]?.let { it != work.previewAspectRatio } == true
    }
    fun parameterValues(work: Work?): Map<String, String> = work?.let { parameterDrafts[it.id] ?: it.parameterValues }.orEmpty()
    fun updateParameter(workId: String, name: String, value: String?) {
        val work = session.worksState.value.find { it.id == workId } ?: return
        val next = parameterValues(work).toMutableMap()
        if (value == null) next.remove(name) else next[name] = value
        parameterDrafts[workId] = next
    }
    fun scheduleParameterSave(workId: String) {
        parameterJobs.remove(workId)?.cancel()
        parameterJobs[workId] = scope.launch { delay(500); commandJob?.join(); saveParameters(workId)?.join() }
    }
    fun flushParameterSave(workId: String): Job {
        parameterJobs.remove(workId)?.cancel()
        return scope.launch { commandJob?.join(); saveParameters(workId)?.join() }
    }
    private fun saveParameters(workId: String): Job? {
        val values = parameterDrafts[workId] ?: return null
        if (session.worksState.value.none { it.id == workId }) return null
        return execute(blockUi = false) {
            val next = snapshots()
            next.first { it.id == workId }.apply {
                parameterValues.clear(); parameterValues.putAll(values); updatedAt = System.currentTimeMillis()
            }
            persist(next, session.activeWorkIdState.value)
            commit(next)
            if (parameterDrafts[workId] == values) parameterDrafts.remove(workId)
        }
    }

    fun chooseFolder(uri: Uri) = execute("作品ファイルを読み込めません。元のファイルは上書きしていません") {
        val folderRepository = requireNotNull(folders)
        val current = snapshots(includeEdits = true)
        val currentId = session.activeWorkIdState.value
        val stored = withContext(io) {
            folderRepository.takePermission(uri)
            val loaded = persistence.loadFolder(uri)
            if (loaded != null && loaded.works.any { it.id == "gravity" && it.p5Version == P5_VERSION_CURRENT }) {
                val migrated = loaded.works.map { snapshotWork(it).apply { if (id == "gravity") p5Version = P5_VERSION_LEGACY } }
                check(persistence.save(uri, migrated, loaded.activeWorkId))
                return@withContext WorkStore(migrated, loaded.activeWorkId)
            }
            // An unreadable store must be rejected by the persistence boundary.
            if (loaded == null || loaded.works.isEmpty()) check(persistence.save(uri, current, currentId))
            loaded
        }
        if (stored != null && stored.works.isNotEmpty()) {
            val id = stored.activeWorkId.takeIf { candidate -> stored.works.any { it.id == candidate } }
                ?: stored.works.first().id
            session.clearAuxiliaryEditors()
            clearMetadataDrafts()
            commit(stored.works, id, replaceEditor = true)
        } else commit(current, savedEdits = true)
        folderRepository.select(uri)
        selectedFolderUri = uri
        emit(WorkEvent(folderChanged = true, rerun = true))
    }

    fun importJs(uri: Uri) = execute("JSファイルを読み込めませんでした") {
        val work = requireNotNull(transfer).readJs(uri)
        val next = snapshots(includeEdits = true) + work
        persist(next, work.id)
        commit(next, work.id, replaceEditor = true, savedEdits = true)
        emit(WorkEvent(rerun = true))
    }
    fun exportJs(uri: Uri) = execute("JSファイルを書き出せませんでした") {
        requireNotNull(transfer).writeJs(uri, session.editorValueState.value.text)
    }
    fun exportBackup(uri: Uri) = execute("バックアップを書き出せませんでした") {
        requireNotNull(transfer).writeBackup(uri, snapshots(includeEdits = true), session.activeWorkIdState.value,
            requireNotNull(settings).state.toBackupJson())
    }
    fun importBackup(uri: Uri) = execute("バックアップを復元できませんでした") {
        val repository = requireNotNull(transfer)
        val backup = repository.readBackup(uri)
        require(backup.store.works.isNotEmpty())
        val restoredSettings = backup.settings?.let(::JSONObject)
        // Validate settings before committing work/snapshot files.
        restoredSettings?.let { requireNotNull(settings).state.restoredFromBackup(it) }
        val id = backup.store.activeWorkId.takeIf { candidate -> backup.store.works.any { it.id == candidate } }
            ?: backup.store.works.first().id
        val folder = selectedFolderUri
        check(repository.commitBackup(backup) { persistence.save(folder, backup.store.works, id) })
        session.clearAuxiliaryEditors()
        clearMetadataDrafts()
        commit(backup.store.works, id, replaceEditor = true)
        restoredSettings?.let { requireNotNull(settings).restoreBackup(it) }
        clearDraft()
        emit(WorkEvent(rerun = true))
    }
    fun exportWorkZip(uri: Uri) = execute("作品ZIPを保存できませんでした") {
        val work = snapshots(includeEdits = true).first { it.id == session.activeWorkIdState.value }
        requireNotNull(transfer).writeBackup(uri, listOf(work), work.id, "{}")
        emit(WorkEvent(message = "作品ZIPを保存しました"))
    }
    fun importWorkZip(uri: Uri) = execute("作品ZIPを読み込めませんでした") {
        val repository = requireNotNull(transfer)
        val backup = repository.readWorkZip(uri)
        val next = snapshots() + backup.store.works
        val id = session.activeWorkIdState.value
        val folder = selectedFolderUri
        check(repository.commitBackup(backup) { persistence.save(folder, next, id) })
        commit(next)
        emit(WorkEvent(message = "作品ZIPを追加しました"))
    }
    fun resetP5Results() { p5Sketches = emptyList(); p5Error = null }
    fun loadP5Account() {
        val username = requireNotNull(settings).p5Username.trim()
        if (p5Busy || !validP5Username(username)) return
        p5Busy = true; resetP5Results()
        scope.launch {
            try {
                p5Sketches = requireNotNull(transfer).fetchAccount(username)
                settings.p5Username = username
                if (p5Sketches.isEmpty()) p5Error = "公開作品がありません"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { p5Error = "p5.jsの作品を取得できませんでした" }
            finally { p5Busy = false }
        }
    }
    fun importFromP5(sketch: P5Sketch, preview: PreviewAssets) {
        if (p5Busy) return
        execute("作品を取り込めませんでした。ファイル数・容量・通信環境を確認してください") {
            p5Busy = true; p5Error = null
            try {
                val existingWorks = snapshots()
                withContext(io) { (persistence as? WorkStoreRepository)?.pruneUnusedAssets(existingWorks, preview) }
                val work = requireNotNull(transfer).readP5(sketch)
                val next = snapshots(includeEdits = true) + work
                persist(next, work.id)
                commit(next, work.id, replaceEditor = true, savedEdits = true)
                showP5Import = false
                emit(WorkEvent(rerun = true, message = "p5.jsの作品を取り込みました"))
            } finally { p5Busy = false }
        }
    }

    override fun onCleared() { settings?.onDraftRecoveryDisabled = null; drafts?.close(); super.onCleared() }
}
