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

internal enum class WorkOperationState { IDLE, SAVING, SAVED, FAILED, CONFLICT }
private class WorkConflictException : IllegalStateException()

/** Owns work commands and publishes state only after durable persistence succeeds. */
internal class WorkManagementViewModel(
    private val session: EditorSessionViewModel,
    val persistence: WorkPersistence,
    private val drafts: DraftSnapshotRepository? = null,
    private val transfer: WorkTransferRepository? = null,
    private val settings: SettingsViewModel? = null,
    private val folders: WorkFolderRepository? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val operationScope: CoroutineScope? = null,
    private val templatePersistence: UserTemplatePersistence? = null
) : ViewModel() {
    var workMenuExpanded by mutableStateOf(false)
    var workSettingsMenuExpanded by mutableStateOf(false)
    var workActionsMenuExpanded by mutableStateOf(false)
    var showSamplePrompt by mutableStateOf(true)
    var showAddDialog by mutableStateOf(false)
    var showSaveTemplateDialog by mutableStateOf(false)
    var showTemplateManager by mutableStateOf(false)
    var showRestoreConfirmation by mutableStateOf(false)
    var showConflictDialog by mutableStateOf(false)
    var operationState by mutableStateOf(WorkOperationState.IDLE)
        private set
    var loadFailure by mutableStateOf<WorkLoadResult.Failed?>(null)
        private set
    var pendingDraft by mutableStateOf<Pair<DraftSnapshot, Map<String, String>>?>(null)
        private set
    private val deferredWorkIds = mutableSetOf<String>()
    private var needsInitialSave = false
    private var conflictPending = false
    var userTemplates by mutableStateOf<List<Work>>(emptyList())
        private set
    var templateLoadFailed by mutableStateOf(false)
        private set
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
    var galleryRenameWorkId by mutableStateOf<String?>(null)
    var galleryDeleteIds by mutableStateOf<Set<String>>(emptySet())
    var galleryTagIds by mutableStateOf<Set<String>>(emptySet())
    private var deletionUndo: DeletedWorkBatch? = null
    private var deletionSequence = 0L
    var deletionUndoToken by mutableStateOf<Long?>(null)
        private set
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
    var copySampleId by mutableStateOf<String?>(null)
    var galleryMoveIds by mutableStateOf<Set<String>>(emptySet())
    var showGalleryFolders by mutableStateOf(false)
    var newWorkFolder by mutableStateOf("")
    private var emptyGalleryFolders by mutableStateOf<Set<String>>(emptySet())
    var galleryTabs by mutableStateOf<List<String>>(listOf(SAMPLE_FOLDER))
        private set
    val galleryFolders: List<String>
        get() = galleryTabs.filter { it != SAMPLE_FOLDER && it.isNotEmpty() }

    private fun syncGalleryTabs(preferred: List<String>, emptyFolders: Set<String>, works: List<Work>): List<String> {
        val userFolders = (emptyFolders + works.filterNot { it.isSample }.map { it.folderName }.filter { it.isNotBlank() })
        val result = mutableListOf<String>()
        preferred.forEach { tab ->
            if (tab == SAMPLE_FOLDER) {
                if (SAMPLE_FOLDER !in result) result.add(SAMPLE_FOLDER)
            } else if (tab == "") {
                if (userFolders.isNotEmpty() && "" !in result) result.add("")
            } else if (tab in userFolders) {
                if (tab !in result) result.add(tab)
            }
        }
        if (SAMPLE_FOLDER !in result) result.add(0, SAMPLE_FOLDER)
        if (userFolders.isNotEmpty() && "" !in result) {
            val sampleIdx = result.indexOf(SAMPLE_FOLDER)
            if (sampleIdx != -1) result.add(sampleIdx + 1, "") else result.add(0, "")
        }
        userFolders.forEach { folder ->
            if (folder !in result) result.add(folder)
        }
        return result
    }

    fun requestSampleCopy(workId: String = session.activeWorkIdState.value) {
        if (session.worksState.value.any { it.id == workId && it.isSample }) copySampleId = workId
    }
    private fun requireEditable(workId: String = session.activeWorkIdState.value) {
        require(session.worksState.value.any { it.id == workId && !it.isSample }) { "Sample originals are read-only" }
    }
    var officialSamples by mutableStateOf<List<Work>>(emptyList())
        private set

    init { settings?.onDraftRecoveryDisabled = { drafts?.clear() } }


    fun notifyPreviewUpdated(workId: String) { updatedPreviewId = workId; previewRevision++ }

    suspend fun initialize(defaultWorks: () -> List<Work>) {
        if (session.initialized) return
        loadUserTemplates()
        val samples = withContext(io) { defaultWorks() }
        selectedFolderUri = withContext(io) { folders?.validUri() }
        val result = withContext(io) { persistence.loadResult(selectedFolderUri) }
        val store = when (result) {
            is WorkLoadResult.Loaded -> {
                deferredWorkIds.addAll(result.deferredWorkIds)
                if (result.recovered) emit(WorkEvent(message = "前の保存データから復旧しました"))
                result.store
            }
            WorkLoadResult.Empty -> { needsInitialSave = true; WorkStore(emptyList(), "") }
            is WorkLoadResult.Failed -> { loadFailure = result; WorkStore(emptyList(), "") }
        }
        officialSamples = sampleCatalog(samples, store.works)
        emptyGalleryFolders = withContext(io) { folders?.galleryFolders(selectedFolderUri).orEmpty() }
        val loadedOrder = withContext(io) { folders?.galleryTabsOrder(selectedFolderUri).orEmpty() }
        galleryTabs = syncGalleryTabs(loadedOrder, emptyGalleryFolders, store.works)
        val remembered = persistence.selectedWorkId(selectedFolderUri)
        val activeId = remembered?.takeIf { id -> officialSamples.any { it.id == id } }
            ?: store.activeWorkId.takeIf { id -> store.works.any { it.id == id } }
            ?: store.works.firstOrNull()?.id ?: officialSamples.firstOrNull()?.id.orEmpty()
        val rawDraft = if (settings?.draftRecovery != false) withContext(io) { drafts?.load() } else null
        // Only files with drafts need a saved body at startup; other gallery items stay deferred.
        var works = store.works
        if (rawDraft != null && rawDraft.first.storeKey == draftStoreKey(selectedFolderUri)) {
            val needed = works.filter { work -> work.id == rawDraft.first.workId ||
                rawDraft.second.keys.any { it.startsWith("${work.id}/") } }.map { it.id }.toSet()
            works = withContext(io) { works.map { work ->
                if (work.id in needed && work.id in deferredWorkIds) runCatching { persistence.loadWork(selectedFolderUri, work.id) }.getOrNull() ?: work else work
            } }
            deferredWorkIds.removeAll(works.filter { it.bodyLoaded }.map { it.id }.toSet())
        }
        val compatible = if (rawDraft != null) withContext(io) { drafts?.loadCompatible(draftStoreKey(selectedFolderUri), works.filter { it.bodyLoaded }) } else null
        if (rawDraft != null && (compatible == null || compatible.second != rawDraft.second ||
                (compatible.first.workId != activeId && compatible.first.code != works.firstOrNull { it.id == compatible.first.workId }?.code))) pendingDraft = rawDraft
        compatible?.second?.let(session.fileDrafts::putAll)
        session.initialize(if (loadFailure == null) works + officialSamples else works, activeId, compatible?.first?.takeIf { it.workId == activeId }?.code)
    }

    fun saveDraft() {
        if (settings?.draftRecovery != false && session.initialized && pendingDraft == null && loadFailure == null) {
            val work = session.worksState.value.firstOrNull { it.id == session.activeWorkIdState.value } ?: return
            if (work.isSample) return
            val activeHash = draftBaseHash(work)
            drafts?.save(work.id, session.editorValueState.value.text, session.fileDrafts.toMap(),
                draftStoreKey(selectedFolderUri), activeHash,
                session.worksState.value.filter { saved -> saved.bodyLoaded &&
                    (saved.id == work.id || session.fileDrafts.keys.any { it.startsWith("${saved.id}/") })
                }.associate { it.id to if (it.id == work.id) activeHash else draftBaseHash(it) })
        }
    }
    fun clearDraft() {
        if (pendingDraft != null || loadFailure != null) return
        if (settings?.draftRecovery != false &&
            (session.fileDrafts.isNotEmpty() || session.editorValueState.value.text != session.lastSavedTextState.value)) {
            saveDraft()
        } else drafts?.clear()
    }

    private fun emit(event: WorkEvent) { eventChannel.trySend(event) }
    private fun execute(errorText: String = "保存できませんでした。保存先を確認して再試行してください",
                        blockUi: Boolean = true, progressDelayMillis: Long = 350L, locksEditor: Boolean = false,
                        action: suspend () -> Unit): Job? {
        if (workSaving || session.assetBusy || session.snapshotOperationWorkId != null) return null
        workSaving = true
        showBlockingProgress = blockUi && progressDelayMillis == 0L
        session.assetBusy = true
        session.editorInputLocked = locksEditor
        return scope.launch {
            val progress = if (blockUi && progressDelayMillis > 0) launch {
                delay(progressDelayMillis)
                showBlockingProgress = true
            } else null
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: WorkConflictException) {
                conflictPending = true
                operationState = WorkOperationState.CONFLICT
                emit(WorkEvent(message = "保存先に別の変更があります。外部変更と統合してください", failure = true))
            }
            catch (_: Exception) {
                if (conflictPending) operationState = WorkOperationState.CONFLICT
                else if (operationState == WorkOperationState.SAVING) operationState = WorkOperationState.FAILED
                emit(WorkEvent(message = errorText, failure = true))
            }
            finally {
                progress?.cancel()
                workSaving = false; showBlockingProgress = false; session.assetBusy = false; session.editorInputLocked = false
            }
        }.also { commandJob = it }
    }

    private fun snapshots(includeEdits: Boolean = false, copyIds: Set<String>? = null): List<Work> {
        val selected = session.activeWorkIdState.value
        val code = session.editorValueState.value.text
        return session.worksState.value.map { original ->
            // Committed Work objects are replaced by commands, never mutated in place.
            // Reuse untouched works; copy anything this command will edit or commit.
            if (original.isSample) return@map snapshotWork(original)
            val dirty = includeEdits && (
                (original.id == selected && original.code != code) ||
                    parameterDrafts.containsKey(original.id) || ratioDrafts.containsKey(original.id) ||
                    original.files.any { (name, saved) ->
                        session.fileDrafts["${original.id}/$name"]?.let { it != saved } == true
                    })
            if (copyIds != null && original.id !in copyIds && !dirty) return@map original
            snapshotWork(original).also { copy ->
                if (includeEdits) {
                    parameterDrafts[copy.id]?.let { copy.parameterValues.clear(); copy.parameterValues.putAll(it) }
                    ratioDrafts[copy.id]?.let { copy.previewAspectRatio = it }
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

    private suspend fun persist(works: List<Work>, activeId: String, resolvingConflict: Boolean = false) {
        val folder = selectedFolderUri
        if (conflictPending && !resolvingConflict) throw WorkConflictException()
        check(loadFailure == null)
        operationState = WorkOperationState.SAVING
        val userStore = userWorkStore(works, activeId)
        when (withContext(io) { persistence.saveResult(folder, userStore.works, userStore.activeWorkId) }) {
            WorkSaveResult.Saved -> {
                operationState = WorkOperationState.SAVED; needsInitialSave = false
                if (works.any { it.id == activeId && it.isSample }) persistence.rememberSelectedWork(folder, activeId)
            }
            WorkSaveResult.Conflict -> throw WorkConflictException()
            WorkSaveResult.Failed -> error("Cannot save work store")
        }
    }

    private suspend fun hydrate(workId: String) {
        if (workId !in deferredWorkIds) return
        val body = withContext(io) { persistence.loadWork(selectedFolderUri, workId) } ?: error("Missing work body")
        session.worksState.value = session.worksState.value.map { if (it.id == workId) body else it }
        deferredWorkIds.remove(workId)
    }

    private suspend fun materialize() {
        if (deferredWorkIds.isEmpty()) return
        val all = withContext(io) { persistence.materializeWorks(selectedFolderUri, session.worksState.value) }
        session.worksState.value = all
        deferredWorkIds.clear()
    }

    fun retryLoad() = execute(errorText = "作品を読み込めませんでした。保存先を再接続してください", locksEditor = true) {
        val result = withContext(io) { persistence.loadResult(selectedFolderUri) }
        when (result) {
            is WorkLoadResult.Failed -> { loadFailure = result; return@execute }
            is WorkLoadResult.Loaded -> {
                deferredWorkIds.clear(); deferredWorkIds.addAll(result.deferredWorkIds)
                loadFailure = null
                commit(result.store.works, result.store.activeWorkId, replaceEditor = true)
                if (result.recovered) emit(WorkEvent(message = "前の保存データから復旧しました"))
            }
            WorkLoadResult.Empty -> {
                loadFailure = null; needsInitialSave = true; deferredWorkIds.clear()
                if (officialSamples.isNotEmpty()) commit(officialSamples, officialSamples.first().id, replaceEditor = true)
            }
        }
        emit(WorkEvent(rerun = true))
    }

    fun mergeExternalChanges() = execute(errorText = "変更を統合できませんでした。編集内容は保持しています", locksEditor = true) {
        materialize()
        val baseline = session.worksState.value.filterNot { it.isSample }.map(::snapshotWork)
        val edited = snapshots(includeEdits = true).filterNot { it.isSample }
        val result = withContext(io) { persistence.loadResult(selectedFolderUri, eager = true) }
        val remote = when (result) {
            is WorkLoadResult.Loaded -> result.store.works
            WorkLoadResult.Empty -> emptyList()
            is WorkLoadResult.Failed -> error("Cannot load external works")
        }
        val merged = mergeConcurrentWorks(baseline, edited, remote, session.activeWorkIdState.value)
        require(merged.works.isNotEmpty())
        persist(merged.works, merged.activeId, resolvingConflict = true)
        conflictPending = false
        session.clearAuxiliaryEditors(); clearMetadataDrafts(); deferredWorkIds.clear()
        commit(merged.works, merged.activeId, replaceEditor = true)
        showConflictDialog = false
        emit(WorkEvent(rerun = true, message = if (merged.copies > 0)
            "外部変更と統合しました。競合した編集は別作品として保持しました" else "外部変更と統合しました"))
    }

    fun recoverPendingDraftAsWork() = execute(errorText = "下書きを復元できませんでした", locksEditor = true) {
        val pending = pendingDraft ?: return@execute
        materialize()
        val recovered = recoveredDraftWorks(pending.first, pending.second, session.worksState.value,
            pending.first.storeKey == draftStoreKey(selectedFolderUri))
        val next = snapshots(includeEdits = true) + recovered
        persistAndSwitch(next, recovered.first().id)
        pendingDraft = null
        clearDraft()
        emit(WorkEvent(rerun = true, message = "下書きを別作品として復元しました"))
    }

    fun dismissPendingDraft() {
        pendingDraft = null
        drafts?.clear()
        clearDraft()
    }

    /** Saves any later IME input before replacing the selected editor, even with a queued callback. */
    private suspend fun persistAndSwitch(next: List<Work>, activeId: String) {
        persist(next, activeId)
        commit(next, savedEdits = true)
        while (hasEditsToSave()) {
            val latest = snapshots(includeEdits = true, copyIds = emptySet())
            persist(latest, activeId)
            commit(latest, savedEdits = true)
        }
        hydrate(activeId)
        commit(session.worksState.value, activeId, replaceEditor = true)
    }

    private fun commit(works: List<Work>, activeId: String = session.activeWorkIdState.value,
                       replaceEditor: Boolean = false, savedEdits: Boolean = false, resumeEditor: Boolean = false) {
        val users = works.filterNot { it.isSample }
        if (officialSamples.any { sample -> users.any { it.id == sample.id } })
            officialSamples = sampleCatalog(officialSamples, users)
        val combined = users + officialSamples
        val resolvedId = activeId.takeIf { id -> combined.any { it.id == id } } ?: combined.firstOrNull()?.id.orEmpty()
        session.worksState.value = combined
        if (replaceEditor) {
            val active = combined.firstOrNull { it.id == resolvedId }
            session.activateWorkEditor(resolvedId, active?.code.orEmpty(), resumeEditor, combined.map { it.id }.toSet())
        } else if (savedEdits) {
            works.firstOrNull { it.id == session.activeWorkIdState.value }?.let { session.lastSavedTextState.value = it.code }
        }
        if (savedEdits) works.forEach { work ->
            if (parameterDrafts[work.id] == work.parameterValues.toMap()) parameterDrafts.remove(work.id)
            if (ratioDrafts[work.id] == work.previewAspectRatio) ratioDrafts.remove(work.id)
            work.files.forEach { (name, code) ->
                val key = "${work.id}/$name"
                if (session.fileDrafts[key] == code) session.fileDrafts.remove(key)
            }
        }
        clearDraft()
    }

    fun saveCurrentWork(then: WorkEvent = WorkEvent(), blockUi: Boolean = false, progressDelayMillis: Long = 400) =
        execute(blockUi = blockUi, progressDelayMillis = progressDelayMillis) {
        if (!needsInitialSave && !hasEditsToSave() && !conflictPending) {
            operationState = WorkOperationState.SAVED
            emit(then)
            return@execute
        }
        val id = session.activeWorkIdState.value
        val original = session.worksState.value.first { it.id == id }
        val next = snapshots(includeEdits = true, copyIds = setOf(id))
        val replacement = next.first { it.id == id }
        if (session.lastSavedTextState.value != replacement.code) {
            replacement.revisions.add(WorkRevision(session.lastSavedTextState.value, original.updatedAt))
            while (replacement.revisions.size > 30) replacement.revisions.removeAt(0)
        }
        if (hasEditsToSave()) replacement.updatedAt = System.currentTimeMillis()
        persist(next, id)
        commit(next, savedEdits = true)
        emit(then)
    }

    internal fun hasEditsToSave(): Boolean {
        val works = session.worksState.value
        val active = works.firstOrNull { it.id == session.activeWorkIdState.value }
        if (active != null && !active.isSample && session.editorValueState.value.text != active.code) return true
        return works.filterNot { it.isSample }.any { work ->
            parameterDrafts[work.id]?.let { it != work.parameterValues.toMap() } == true ||
                ratioDrafts[work.id]?.let { it != work.previewAspectRatio } == true ||
                work.files.any { (name, code) ->
                    session.fileDrafts["${work.id}/$name"]?.let { it != code } == true
                }
        }
    }

    fun selectWork(workId: String, openMenu: Boolean) = execute(errorText = "作品を読み込めませんでした。編集内容は保持しています", progressDelayMillis = 350, locksEditor = true) {
        val oldId = session.activeWorkIdState.value
        if (workId != oldId) {
            require(session.worksState.value.any { it.id == workId })
            // Clean navigation does not serialize/rewrite all works or replace their objects.
            // If typing continues during an edit save, persist the newer edits before switching.
            while (hasEditsToSave()) {
                val next = snapshots(includeEdits = true, copyIds = emptySet())
                persist(next, workId)
                commit(next, savedEdits = true)
            }
            persistence.rememberSelectedWork(selectedFolderUri, workId)
            hydrate(workId)
            commit(session.worksState.value, workId, replaceEditor = true, resumeEditor = true)
        }
        workMenuExpanded = false
        workActionsMenuExpanded = openMenu
        emit(WorkEvent(rerun = workId != oldId))
    }

    fun createWork(title: String, ratio: String, sizingMode: CanvasSizingMode, template: WorkTemplate) = execute(locksEditor = true) {
        val work = Work(id = java.util.UUID.randomUUID().toString(), title = title,
            code = template.code(sizingMode), previewAspectRatio = ratio, libraries = template.libraries,
            folderName = newWorkFolder.takeIf { it in galleryFolders }.orEmpty())
        val next = snapshots(includeEdits = true) + work
        persistAndSwitch(next, work.id)
        showAddDialog = false
        emit(WorkEvent(rerun = true))
    }

    private suspend fun loadUserTemplates() {
        try {
            val loaded = withContext(io) { templatePersistence?.load().orEmpty() }
            userTemplates = loaded
            templateLoadFailed = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { templateLoadFailed = true }
    }

    fun retryUserTemplates() = execute(blockUi = false) { loadUserTemplates() }

    fun saveUserTemplate(title: String) = execute(errorText = "テンプレートを保存できませんでした") {
        requireEditable()
        require(title.trim().isNotEmpty())
        check(!templateLoadFailed)
        val repository = checkNotNull(templatePersistence)
        val id = session.activeWorkIdState.value
        val current = snapshots(includeEdits = true, copyIds = setOf(id)).first { it.id == id }
        val template = newWorkFromUserTemplate(current, title.trim())
        val next = userTemplates + template
        withContext(io) {
            repository.captureAssets(template)
            repository.save(next)
        }
        userTemplates = next
        showSaveTemplateDialog = false
        emit(WorkEvent(message = "テンプレートを保存しました"))
    }

    fun deleteUserTemplate(templateId: String) = execute(errorText = "テンプレートを削除できませんでした") {
        check(!templateLoadFailed)
        val repository = checkNotNull(templatePersistence)
        require(userTemplates.any { it.id == templateId })
        val next = userTemplates.filterNot { it.id == templateId }
        withContext(io) { repository.save(next) }
        userTemplates = next
        emit(WorkEvent(message = "テンプレートを削除しました"))
    }

    fun renameUserTemplate(templateId: String, title: String) = execute(errorText = "テンプレートを保存できませんでした", blockUi = false) {
        require(title.trim().isNotEmpty())
        check(!templateLoadFailed)
        val next = userTemplates.map { if (it.id == templateId) snapshotWork(it).apply {
            this.title = title.trim(); updatedAt = System.currentTimeMillis()
        } else it }
        require(next.any { it.id == templateId })
        withContext(io) { checkNotNull(templatePersistence).save(next) }
        userTemplates = next
        emit(WorkEvent(message = "テンプレートを保存しました"))
    }

    fun updateUserTemplate(templateId: String) = execute(errorText = "テンプレートを保存できませんでした") {
        requireEditable()
        check(!templateLoadFailed)
        val previous = userTemplates.first { it.id == templateId }
        val current = snapshots(includeEdits = true).first { it.id == session.activeWorkIdState.value }
        val replacement = Work(id = previous.id, title = previous.title, code = current.code,
            files = current.files.toMutableMap(), assets = current.assets.toMap(),
            previewAspectRatio = previewRatio(current), p5Version = current.p5Version,
            p5SoundEnabled = current.p5SoundEnabled, libraries = current.libraries.toMap(),
            parameterValues = current.parameterValues.toMap(), createdAt = previous.createdAt)
        val next = userTemplates.map { if (it.id == templateId) replacement else it }
        withContext(io) { checkNotNull(templatePersistence).captureAssets(replacement); templatePersistence.save(next) }
        userTemplates = next
        emit(WorkEvent(message = "テンプレートを保存しました"))
    }

    fun createWorkFromUserTemplate(title: String, templateId: String) = execute(locksEditor = true) {
        check(!templateLoadFailed)
        val repository = checkNotNull(templatePersistence)
        val template = userTemplates.first { it.id == templateId }
        val work = newWorkFromUserTemplate(template, title.trim()).apply { folderName = newWorkFolder.takeIf { it in galleryFolders }.orEmpty() }
        val next = snapshots(includeEdits = true) + work
        withContext(io) { repository.prepareAssets(template) }
        persistAndSwitch(next, work.id)
        showAddDialog = false
        emit(WorkEvent(rerun = true))
    }

    /** Insert into the main file as one Undo operation. Failed saves keep the edit recoverable. */
    fun insertParameterDeclarations(declarations: String): Boolean {
        if (session.sampleReadOnly) { requestSampleCopy(); return false }
        if (workSaving || session.assetBusy || session.snapshotOperationWorkId != null) return false
        val work = session.worksState.value.firstOrNull { it.id == session.activeWorkIdState.value } ?: return false
        val sources = work.files.mapValues { (name, code) -> session.fileDrafts["${work.id}/$name"] ?: code } +
            ("sketch.js" to session.editorValueState.value.text)
        val existing = workParameters(sources)
        val added = workParameters(mapOf("sketch.js" to declarations))
        val lines = declarations.lineSequence().filter { it.isNotBlank() }.toList()
        if (added.size != lines.size || lines.any { workParameters(mapOf("sketch.js" to it)).size != 1 }) return false
        if (added.isEmpty() || existing.size + added.size > 16 || added.any { item -> existing.any { it.name == item.name } })
            return false
        val before = session.editorValueState.value
        val next = prependDeclarations(before, declarations)
        session.applyChange(before, next)
        session.editorValueState.value = next
        saveCurrentWork(WorkEvent(rerun = true, forceRun = true))
        return true
    }

    /** Writes the current parameter values into the `@rin` declarations of the editing file as new defaults. */
    fun applyParameterDefaults(): Boolean {
        if (session.sampleReadOnly) { requestSampleCopy(); return false }
        if (workSaving || session.assetBusy || session.snapshotOperationWorkId != null) return false
        val work = session.worksState.value.firstOrNull { it.id == session.activeWorkIdState.value } ?: return false
        val before = session.editorValueState.value
        val (text, names) = applyParameterDefaults(before.text, parameterValues(work)) ?: return false
        val next = before.copy(text = text, selection = androidx.compose.ui.text.TextRange(
            before.selection.start.coerceAtMost(text.length), before.selection.end.coerceAtMost(text.length)))
        session.applyChange(before, next)
        session.editorValueState.value = next
        names.forEach { updateParameter(work.id, it, null) }
        saveCurrentWork(WorkEvent(rerun = true, forceRun = true))
        return true
    }

    fun copySample(title: String, folder: String) = execute("サンプルをコピーできませんでした", locksEditor = true) {
        val source = session.worksState.value.first { it.id == copySampleId && it.isSample }
        require(title.trim().isNotEmpty() && title.trim().length <= 120)
        require(folder.isEmpty() || folder in galleryFolders)
        val copy = copyGalleryWork(source, emptySet()).apply {
            this.title = title.trim(); folderName = folder
            parameterValues.clear(); parameterValues.putAll(this@WorkManagementViewModel.parameterValues(source))
        }
        persistAndSwitch(snapshots(includeEdits = true) + copy, copy.id)
        copySampleId = null
        workMenuExpanded = false
        emit(WorkEvent(rerun = true, message = "自分の作品にコピーしました"))
    }

    fun createGalleryFolder(name: String) = execute("フォルダーを作成できませんでした", blockUi = false) {
        val normalized = name.trim()
        require(validGalleryFolder(normalized) && normalized !in galleryFolders)
        val names = emptyGalleryFolders + galleryFolders + normalized
        val updatedTabs = syncGalleryTabs(galleryTabs + normalized, names, session.worksState.value)
        galleryTabs = updatedTabs
        withContext(io) { folders?.saveGalleryTabsOrder(selectedFolderUri, updatedTabs) }
        emptyGalleryFolders = names
    }

    fun renameGalleryFolder(old: String, name: String) = execute("フォルダーを変更できませんでした", blockUi = false) {
        val normalized = name.trim()
        require(old in galleryFolders && validGalleryFolder(normalized) && (old == normalized || normalized !in galleryFolders))
        val next = snapshots(copyIds = session.worksState.value.filter { !it.isSample && it.folderName == old }.map { it.id }.toSet())
        next.filter { !it.isSample && it.folderName == old }.forEach { it.folderName = normalized }
        persist(next, session.activeWorkIdState.value)
        commit(next)
        val names = (emptyGalleryFolders + galleryFolders - old) + normalized
        val updatedTabs = galleryTabs.map { if (it == old) normalized else it }
        galleryTabs = syncGalleryTabs(updatedTabs, names, next)
        withContext(io) { folders?.saveGalleryTabsOrder(selectedFolderUri, galleryTabs) }
        emptyGalleryFolders = names
    }

    fun deleteGalleryFolder(name: String) = execute("フォルダーを削除できませんでした", blockUi = false) {
        require(name in galleryFolders)
        val next = snapshots(copyIds = session.worksState.value.filter { !it.isSample && it.folderName == name }.map { it.id }.toSet())
        next.filter { !it.isSample && it.folderName == name }.forEach { it.folderName = "" }
        persist(next, session.activeWorkIdState.value)
        commit(next)
        val names = emptyGalleryFolders - name
        val updatedTabs = galleryTabs.filter { it != name }
        galleryTabs = syncGalleryTabs(updatedTabs, names, next)
        withContext(io) { folders?.saveGalleryTabsOrder(selectedFolderUri, galleryTabs) }
        emptyGalleryFolders = names
    }

    fun reorderGalleryTabs(newTabs: List<String>) = execute(blockUi = false) {
        val synced = syncGalleryTabs(newTabs, emptyGalleryFolders, session.worksState.value)
        galleryTabs = synced
        withContext(io) { folders?.saveGalleryTabsOrder(selectedFolderUri, synced) }
    }

    fun moveGalleryWorks(ids: Set<String>, folder: String) = execute("作品を移動できませんでした", blockUi = false) {
        require(ids.isNotEmpty() && (folder.isEmpty() || folder in galleryFolders))
        ids.forEach(::requireEditable)
        val next = snapshots(copyIds = ids)
        next.filter { it.id in ids }.forEach { it.folderName = folder }
        persist(next, session.activeWorkIdState.value)
        commit(next)
        galleryMoveIds = emptySet()
        emit(WorkEvent(message = "作品を移動しました"))
    }

    fun duplicateWork() = execute(locksEditor = true) {
        requireEditable()
        val current = snapshots(includeEdits = true).first { it.id == session.activeWorkIdState.value }
        val duplicate = Work(id = java.util.UUID.randomUUID().toString(), title = "${current.title} copy",
            code = current.code, files = current.files.toMutableMap(), assets = current.assets.toMap(),
            previewAspectRatio = current.previewAspectRatio, p5Version = current.p5Version,
            p5SoundEnabled = current.p5SoundEnabled, libraries = current.libraries.toMap(),
            parameterValues = current.parameterValues.toMap(), isPinned = current.isPinned, tags = current.tags.toList(), folderName = current.folderName)
        val next = snapshots(includeEdits = true) + duplicate
        persistAndSwitch(next, duplicate.id)
        emit(WorkEvent(rerun = true))
    }

    private fun changeWork(workId: String, event: WorkEvent = WorkEvent(), change: (Work) -> Unit) = execute(blockUi = false) {
        requireEditable(workId)
        val next = snapshots(copyIds = setOf(workId))
        val target = next.first { it.id == workId }
        change(target)
        target.updatedAt = System.currentTimeMillis()
        persist(next, session.activeWorkIdState.value)
        commit(next)
        emit(event)
    }
    fun renameWork(workId: String, title: String) = execute {
        requireEditable(workId)
        val next = snapshots(copyIds = setOf(workId))
        next.first { it.id == workId }.apply { this.title = title; updatedAt = System.currentTimeMillis() }
        persist(next, session.activeWorkIdState.value)
        commit(next)
        showRenameDialog = false
        galleryRenameWorkId = null
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
    /** Load bodies before deletion/duplication/export without committing placeholder content. */
    private suspend fun operationWorks(ids: Set<String>): List<Work> {
        val captured = snapshots(includeEdits = true, copyIds = ids)
        require(ids.isNotEmpty() && ids.all { id -> captured.any { it.id == id } })
        return captured.map { work ->
            if (work.id in ids && !work.bodyLoaded) {
                withContext(io) { persistence.loadWork(selectedFolderUri, work.id) }
                    ?: error("Missing work body")
            } else work
        }
    }

    fun duplicateGalleryWork(workId: String) = execute("作品を複製できませんでした", locksEditor = true) {
        requireEditable(workId)
        val captured = operationWorks(setOf(workId))
        val copy = copyGalleryWork(captured.first { it.id == workId }, captured.map { it.title }.toSet())
        val next = captured + copy
        persist(next, session.activeWorkIdState.value)
        commit(next, savedEdits = true)
        emit(WorkEvent(message = "作品を複製しました"))
    }

    fun tagGalleryWorks(ids: Set<String>, rawTag: String, remove: Boolean = false) = execute(blockUi = false) {
        ids.forEach(::requireEditable)
        val tag = rawTag.trim().removePrefix("#").trim()
        require(tag.isNotBlank())
        val next = snapshots(copyIds = ids)
        require(ids.isNotEmpty() && ids.all { id -> next.any { it.id == id } })
        next.filter { it.id in ids }.forEach { work ->
            val changed = if (remove) work.tags.removeAll { it.equals(tag, true) }
                else if (work.tags.none { it.equals(tag, true) }) { work.tags.add(tag); true } else false
            if (changed) work.updatedAt = System.currentTimeMillis()
        }
        persist(next, session.activeWorkIdState.value)
        commit(next)
        galleryTagIds = emptySet()
        emit(WorkEvent())
    }

    fun deleteCurrentWork() = deleteGalleryWorks(setOf(session.activeWorkIdState.value))

    fun deleteGalleryWorks(ids: Set<String>) = execute("作品を削除できませんでした", locksEditor = true) {
        ids.forEach(::requireEditable)
        val before = operationWorks(ids)
        val survivors = before.filterNot { it.id in ids }
        require(survivors.isNotEmpty()) // Keep the existing last-work protection.
        val oldId = session.activeWorkIdState.value
        val replacingEditor = oldId in ids
        val nextId = if (replacingEditor) survivors.first().id else oldId
        // A missing replacement body must fail before writing the deletion to disk.
        val next = survivors.map { work ->
            if (work.id == nextId && !work.bodyLoaded) {
                withContext(io) { persistence.loadWork(selectedFolderUri, work.id) }
                    ?: error("Missing replacement work")
            } else work
        }
        val removed = before.mapIndexedNotNull { index, work ->
            if (work.id in ids) RemovedGalleryWork(index, snapshotWork(work)) else null
        }
        persist(next, nextId)
        ids.forEach { id ->
            session.clearAuxiliaryEditors(id)
            parameterDrafts.remove(id); ratioDrafts.remove(id); parameterJobs.remove(id)?.cancel()
        }
        deferredWorkIds.removeAll(ids + nextId)
        commit(next, nextId, replaceEditor = replacingEditor, savedEdits = true)
        showDeleteDialog = false
        galleryDeleteIds = emptySet()
        val token = ++deletionSequence
        deletionUndo = DeletedWorkBatch(token, selectedFolderUri?.toString(), removed)
        deletionUndoToken = token
        emit(WorkEvent(rerun = replacingEditor))
    }

    fun undoGalleryDeletion(token: Long) = execute("作品を復元できませんでした", locksEditor = true) {
        val batch = deletionUndo ?: return@execute
        require(batch.token == token && batch.folder == selectedFolderUri?.toString())
        val next = restoreGalleryWorks(snapshots(includeEdits = true), batch.removed)
        persist(next, session.activeWorkIdState.value)
        commit(next, savedEdits = true)
        batch.removed.forEach { deferredWorkIds.remove(it.work.id) }
        dismissGalleryDeletion(token)
        emit(WorkEvent(message = "作品を復元しました"))
    }

    fun dismissGalleryDeletion(token: Long) {
        if (deletionUndoToken == token) {
            deletionUndo = null
            deletionUndoToken = null
        }
    }

    private fun retainedUndoWorks(): List<Work> = deletionUndo?.removed?.map { it.work }.orEmpty()

    fun addSamples(samples: List<Work>, fromPrompt: Boolean = false) = execute {
        val next = snapshots() + samples.map { copyGalleryWork(it, snapshots().map { work -> work.title }.toSet()) }
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
    fun requestRestoreCurrentWork() {
        if (hasEditsToSave()) showRestoreConfirmation = true else restoreCurrentWork()
    }

    fun restoreCurrentWork() = execute(locksEditor = true) {
        requireEditable()
        val id = session.activeWorkIdState.value
        val folder = selectedFolderUri
        val result = withContext(io) { persistence.loadResult(folder, eager = true) }
        val store = (result as? WorkLoadResult.Loaded)?.store ?: error("Missing saved work")
        val restored = store.works.find { it.id == id } ?: error("Missing saved work")
        val next = store.works.map { if (it.id == id) restored else it }
        session.clearAuxiliaryEditors(id)
        clearMetadataDrafts(id)
        commit(next, id, replaceEditor = true)
        deferredWorkIds.clear()
        showRestoreConfirmation = false
        emit(WorkEvent(rerun = true))
    }
    fun restoreRevision(revision: WorkRevision) = execute(locksEditor = true) {
        requireEditable()
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
        requireEditable(workId)
        validateAssetSet(updated)
        val next = snapshots(copyIds = setOf(workId))
        val target = next.first { it.id == workId }
        target.assets.clear(); target.assets.putAll(updated)
        target.updatedAt = System.currentTimeMillis()
        persist(next, session.activeWorkIdState.value)
        commit(next)
        if (session.activeWorkIdState.value == workId) session.assetPreviewRevision++
    }

    fun renameAsset(request: AssetRenameRequest) = execute("素材名と参照パスを保存できませんでした。編集内容は保持しています", locksEditor = true) {
        requireEditable()
        val id = session.activeWorkIdState.value
        val next = snapshots(includeEdits = true, copyIds = setOf(id))
        val target = next.first { it.id == id }
        require(request.oldName in target.assets && validAssetName(request.newName))
        require(request.oldName == request.newName || request.newName !in target.assets)
        val sources = target.files.toMap() + ("sketch.js" to target.code)
        val updated = withContext(Dispatchers.Default) { applyAssetRename(sources, request) }
        validateProjectTextFiles(updated - "sketch.js", updated.getValue("sketch.js"))
        val changedFiles = sources.keys.filter { sources[it] != updated[it] }
        val editorUpdates = changedFiles.associateWith { file ->
            val before = if (file == "sketch.js") session.editorValueState.value else
                session.fileEditorValues["$id/$file"]?.value ?: TextFieldValue(sources.getValue(file))
            check(before.text == sources.getValue(file))
            before to assetRenameEditorValue(before, file, request)
        }
        val asset = target.assets.remove(request.oldName)!!
        target.assets[request.newName] = asset
        target.code = updated.getValue("sketch.js")
        target.files.clear(); target.files.putAll(updated - "sketch.js")
        if (target.code != session.lastSavedTextState.value) {
            target.revisions.add(WorkRevision(session.lastSavedTextState.value, target.updatedAt))
            while (target.revisions.size > 30) target.revisions.removeAt(0)
        }
        target.updatedAt = System.currentTimeMillis()
        persist(next, id)
        // One Undo entry per affected file; persistence failure never reaches this mutation.
        changedFiles.forEach { file ->
            if (file == "sketch.js") {
                val (before, after) = editorUpdates.getValue(file)
                session.applyChange(before, after)
                session.editorValueState.value = after
            } else {
                val key = "$id/$file"
                val state = session.fileEditorValues.getOrPut(key) { mutableStateOf(TextFieldValue(sources.getValue(file))) }
                val (before, after) = editorUpdates.getValue(file)
                val undo = session.fileUndoStacks.getOrPut(key) { androidx.compose.runtime.mutableStateListOf() }
                val redo = session.fileRedoStacks.getOrPut(key) { androidx.compose.runtime.mutableStateListOf() }
                session.applyChange(before, after, undo, redo)
                state.value = after
                session.fileDrafts[key] = after.text
            }
        }
        commit(next, savedEdits = true)
        session.assetPreviewRevision++
        emit(WorkEvent(message = "素材名と選択した参照パスを更新しました"))
    }

    fun addAssets(workId: String, uris: List<Uri>, preview: PreviewAssets) =
        execute("素材を追加できませんでした。ファイル名・サイズ・保存先を確認してください") {
            requireEditable(workId)
            val next = snapshots()
            val target = next.first { it.id == workId }
            withContext(io) { (persistence as? WorkStoreRepository)?.pruneUnusedAssets(next + retainedUndoWorks(), preview) }
            val updated = requireNotNull(transfer).readAssets(uris, target.assets.toMap())
            target.assets.clear(); target.assets.putAll(updated)
            target.updatedAt = System.currentTimeMillis()
            persist(next, session.activeWorkIdState.value)
            commit(next)
            if (session.activeWorkIdState.value == workId) session.assetPreviewRevision++
        }

    internal suspend fun commitSnapshotRestore(snapshot: WorkSnapshot) {
        requireEditable()
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
        requireEditable()
        validateProjectTextFiles(updatedFiles, session.editorValueState.value.text)
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
    fun saveRuntime(version: String, sound: Boolean, libraries: Map<String, String>,
                    config: ProjectDocumentConfig? = null) = execute {
        requireEditable()
        val next = snapshots()
        val id = session.activeWorkIdState.value
        val configKey = "$id/$PROJECT_CONFIG_FILE"
        val capturedConfigDraft = session.fileDrafts[configKey]
        next.first { it.id == id }.apply {
            p5Version = normalizedP5Version(version); p5SoundEnabled = sound; this.libraries = normalizedWorkLibraries(libraries)
            if (config != null) {
                val latest = files.mapValues { (name, saved) -> session.fileDrafts["$id/$name"] ?: saved }
                val configured = withProjectDocumentConfig(latest, config)
                files.clear(); files.putAll(configured)
            }
            updatedAt = System.currentTimeMillis()
        }
        persist(next, id)
        if (config != null && session.fileDrafts[configKey] == capturedConfigDraft)
            session.clearAuxiliaryEditor(id, PROJECT_CONFIG_FILE)
        commit(next)
        showRuntimeDialog = false
        emit(WorkEvent(forceRun = true))
    }
    private var ratioJob: Job? = null

    fun previewRatio(work: Work?): String = normalizedPreviewAspectRatio(work?.let { ratioDrafts[it.id] ?: it.previewAspectRatio })
    fun draftPreviewRatio(value: String) { if (!session.sampleReadOnly) ratioDrafts[session.activeWorkIdState.value] = normalizedPreviewAspectRatio(value) }
    fun commitPreviewRatio(): Job = savePreviewRatio(previewRatio(session.worksState.value.find { it.id == session.activeWorkIdState.value }))
    fun savePreviewRatio(value: String): Job {
        if (session.sampleReadOnly) return scope.launch { requestSampleCopy() }
        val id = session.activeWorkIdState.value
        val normalized = normalizedPreviewAspectRatio(value)
        draftPreviewRatio(normalized)
        ratioJob?.cancel()
        val job = scope.launch {
            commandJob?.join()
            execute(blockUi = false) {
                val next = snapshots(copyIds = setOf(id))
                next.firstOrNull { it.id == id }?.apply {
                    previewAspectRatio = normalized
                    parameterDrafts[id]?.let {
                        parameterValues.clear()
                        parameterValues.putAll(it)
                    }
                    updatedAt = System.currentTimeMillis()
                }
                try {
                    persist(next, id)
                    commit(next)
                } finally {
                    if (ratioDrafts[id] == normalized) {
                        ratioDrafts.remove(id)
                    }
                }
            }?.join()
        }
        ratioJob = job
        return job
    }

    fun hasPendingMetadata(workId: String): Boolean {
        if (session.worksState.value.any { it.id == workId && it.isSample }) return false
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
        if (session.worksState.value.any { it.id == workId && it.isSample }) return
        parameterJobs.remove(workId)?.cancel()
        parameterJobs[workId] = scope.launch { delay(500); commandJob?.join(); saveParameters(workId)?.join() }
    }
    fun flushParameterSave(workId: String): Job {
        parameterJobs.remove(workId)?.cancel()
        return scope.launch { commandJob?.join(); saveParameters(workId)?.join() }
    }
    private fun saveParameters(workId: String): Job? {
        if (session.worksState.value.any { it.id == workId && it.isSample }) return null
        val values = parameterDrafts[workId] ?: return null
        if (session.worksState.value.none { it.id == workId }) return null
        return execute(blockUi = false) {
            val next = snapshots(copyIds = setOf(workId))
            next.firstOrNull { it.id == workId }?.apply {
                parameterValues.clear(); parameterValues.putAll(values)
                ratioDrafts[workId]?.let { previewAspectRatio = it }
                updatedAt = System.currentTimeMillis()
            }
            persist(next, session.activeWorkIdState.value)
            commit(next)
            if (parameterDrafts[workId] == values) parameterDrafts.remove(workId)
        }
    }

    fun chooseFolder(uri: Uri) = execute("作品ファイルを読み込めません。元のファイルは上書きしていません", locksEditor = true) {
        val folderRepository = requireNotNull(folders)
        if (loadFailure == null) while (hasEditsToSave()) {
            val edits = snapshots(includeEdits = true)
            persist(edits, session.activeWorkIdState.value)
            commit(edits, savedEdits = true)
        }
        materialize()
        val current = snapshots(includeEdits = true).filterNot { it.isSample }
        val currentId = session.activeWorkIdState.value.takeIf { id -> current.any { it.id == id } } ?: current.firstOrNull()?.id.orEmpty()
        val stored = withContext(io) {
            folderRepository.takePermission(uri)
            val result = persistence.loadResult(uri, eager = true)
            require(result !is WorkLoadResult.Failed)
            val loaded = (result as? WorkLoadResult.Loaded)?.store
            // An unreadable store must be rejected by the persistence boundary.
            if (loaded == null || loaded.works.isEmpty()) check(persistence.save(uri, current, currentId))
            loaded
        }
        folderRepository.select(uri)
        selectedFolderUri = uri
        emptyGalleryFolders = withContext(io) { folders?.galleryFolders(uri).orEmpty() }
        val loadedOrder = withContext(io) { folders?.galleryTabsOrder(uri).orEmpty() }
        galleryTabs = syncGalleryTabs(loadedOrder, emptyGalleryFolders, stored?.works ?: current)
        deletionUndoToken?.let(::dismissGalleryDeletion)
        loadFailure = null
        if (stored != null && stored.works.isNotEmpty()) {
            val id = stored.activeWorkId.takeIf { candidate -> stored.works.any { it.id == candidate } }
                ?: stored.works.first().id
            session.clearAuxiliaryEditors()
            clearMetadataDrafts()
            commit(stored.works, id, replaceEditor = true)
        } else commit(current, currentId, replaceEditor = session.activeWorkIdState.value.isBlank(), savedEdits = true)
        deferredWorkIds.clear()
        loadFailure = null
        conflictPending = false
        operationState = WorkOperationState.IDLE
        emit(WorkEvent(folderChanged = true, rerun = true))
    }

    fun importJs(uri: Uri) = execute("JSファイルを読み込めませんでした", locksEditor = true) {
        val work = requireNotNull(transfer).readJs(uri)
        val next = snapshots(includeEdits = true) + work
        persistAndSwitch(next, work.id)
        emit(WorkEvent(rerun = true))
    }
    fun exportJs(uri: Uri) = execute("JSファイルを書き出せませんでした") {
        requireNotNull(transfer).writeJs(uri, session.editorValueState.value.text)
    }
    fun exportBackup(uri: Uri) = execute("バックアップを書き出せませんでした") {
        materialize()
        val users = userWorkStore(snapshots(includeEdits = true), session.activeWorkIdState.value)
        requireNotNull(transfer).writeBackup(uri, users.works, users.activeWorkId,
            requireNotNull(settings).state.toBackupJson())
    }
    fun importBackup(uri: Uri) = execute("バックアップを復元できませんでした", locksEditor = true) {
        val repository = requireNotNull(transfer)
        val backup = repository.readBackup(uri)
        require(backup.store.works.isNotEmpty())
        val restoredSettings = backup.settings?.let(::JSONObject)
        // Validate settings before committing work/snapshot files.
        restoredSettings?.let { requireNotNull(settings).state.restoredFromBackup(it) }
        val id = backup.store.activeWorkId.takeIf { candidate -> backup.store.works.any { it.id == candidate } }
            ?: backup.store.works.first().id
        val folder = selectedFolderUri
        check(repository.commitBackup(backup) {
            when (persistence.saveResult(folder, backup.store.works, id)) {
                WorkSaveResult.Saved -> true
                WorkSaveResult.Conflict -> throw WorkConflictException()
                WorkSaveResult.Failed -> false
            }
        })
        session.clearAuxiliaryEditors()
        clearMetadataDrafts()
        deletionUndoToken?.let(::dismissGalleryDeletion)
        commit(backup.store.works, id, replaceEditor = true)
        deferredWorkIds.clear()
        loadFailure = null
        loadUserTemplates()
        restoredSettings?.let { requireNotNull(settings).restoreBackup(it) }
        clearDraft()
        emit(WorkEvent(rerun = true))
    }
    fun exportWorkZip(uri: Uri) = execute("作品ZIPを保存できませんでした") {
        val work = snapshots(includeEdits = true).first { it.id == session.activeWorkIdState.value }
        requireNotNull(transfer).writeBackup(uri, listOf(work), work.id, "{}", includeTemplates = false)
        emit(WorkEvent(message = "作品ZIPを保存しました"))
    }
    fun exportGalleryWorks(uri: Uri, ids: Set<String>, folderAtRequest: String?) =
        execute("作品ZIPを保存できませんでした") {
            require(folderAtRequest == selectedFolderUri?.toString())
            val selected = operationWorks(ids).filter { it.id in ids }
            requireNotNull(transfer).writeBackup(uri, selected, selected.first().id, "{}", includeTemplates = false)
            emit(WorkEvent(message = "作品ZIPを保存しました"))
        }

    fun importWorkZip(uri: Uri) = execute("作品ZIPを読み込めませんでした") {
        val repository = requireNotNull(transfer)
        val backup = repository.readWorkZip(uri)
        val next = snapshots() + backup.store.works
        val id = session.activeWorkIdState.value
        val folder = selectedFolderUri
        check(repository.commitBackup(backup) {
            when (persistence.saveResult(folder, next, id)) {
                WorkSaveResult.Saved -> true
                WorkSaveResult.Conflict -> throw WorkConflictException()
                WorkSaveResult.Failed -> false
            }
        })
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
        execute("作品を取り込めませんでした。ファイル数・容量・通信環境を確認してください", locksEditor = true) {
            p5Busy = true; p5Error = null
            try {
                val existingWorks = snapshots()
                withContext(io) { (persistence as? WorkStoreRepository)?.pruneUnusedAssets(existingWorks + retainedUndoWorks(), preview) }
                val work = requireNotNull(transfer).readP5(sketch)
                val next = snapshots(includeEdits = true) + work
                persistAndSwitch(next, work.id)
                showP5Import = false
                emit(WorkEvent(rerun = true, message = "p5.jsの作品を取り込みました"))
            } finally { p5Busy = false }
        }
    }

    override fun onCleared() { settings?.onDraftRecoveryDisabled = null; drafts?.close(); super.onCleared() }
}
