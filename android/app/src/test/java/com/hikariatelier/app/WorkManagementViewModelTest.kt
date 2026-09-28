package com.hikariatelier.app

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class WorkManagementViewModelTest {
    private class Store : WorkPersistence {
        var succeed = true
        var calls = 0
        var persisted: WorkStore? = null
        var selectedId: String? = null
        var duringSave: (() -> Unit)? = null
        override fun loadLocal() = persisted
        override fun loadFolder(folderUri: Uri) = persisted
        override fun selectedWorkId(folderUri: Uri?) = selectedId
        override fun rememberSelectedWork(folderUri: Uri?, activeId: String) { selectedId = activeId }
        override fun save(folderUri: Uri?, works: List<Work>, activeId: String): Boolean {
            calls++
            duringSave?.invoke()
            if (succeed) persisted = WorkStore(works.map { snapshotWork(it) }, activeId)
            return succeed
        }
    }
    private fun session(): EditorSessionViewModel = EditorSessionViewModel().apply {
        initialize(listOf(Work("one", "First", "saved", files = mutableMapOf("helper.js" to "old helper")),
            Work("two", "Second", "second saved")), "one")
        editorValueState.value = TextFieldValue("unsaved", TextRange(3))
        fileDrafts["one/helper.js"] = "unsaved helper"
        undoStack.add(TextFieldValue("before editing"))
    }
    private fun vm(session: EditorSessionViewModel, store: Store, scope: CoroutineScope) =
        WorkManagementViewModel(session, store, io = Dispatchers.Unconfined, operationScope = scope)

    @Test fun saveFailurePreservesTextSelectionHistoryAndAuxiliaryDrafts() = runBlocking {
        val session = session(); val store = Store().apply { succeed = false }; val vm = vm(session, store, this)
        vm.saveCurrentWork()!!.join()
        assertEquals(TextFieldValue("unsaved", TextRange(3)), session.editorValueState.value)
        assertEquals("saved", session.lastSavedTextState.value)
        assertEquals("saved", session.worksState.value.first().code)
        assertEquals("unsaved helper", session.fileDrafts["one/helper.js"])
        assertEquals(1, session.undoStack.size)
        assertFalse(vm.workSaving); assertFalse(session.assetBusy)
        assertTrue(vm.events.first().failure)
    }
    @Test fun successfulSaveCommitsBothFilesAndKeepsUndo() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        vm.saveCurrentWork()!!.join()
        assertEquals("unsaved", store.persisted!!.works.first().code)
        assertEquals("unsaved helper", store.persisted!!.works.first().files["helper.js"])
        assertEquals("unsaved", session.lastSavedTextState.value)
        assertFalse(session.fileDrafts.containsKey("one/helper.js"))
        assertEquals(1, session.undoStack.size)
        assertEquals("saved", session.worksState.value.first().revisions.last().code)
    }
    @Test fun typingDuringSaveRemainsUnsavedAfterCommit() = runBlocking {
        val session = session(); val store = Store().apply { duringSave = {
            session.editorValueState.value = TextFieldValue("newer typing")
            session.fileDrafts["one/helper.js"] = "newer helper"
        } }; val vm = vm(session, store, this)
        vm.saveCurrentWork()!!.join()
        assertEquals("unsaved", session.lastSavedTextState.value)
        assertEquals("newer typing", session.editorValueState.value.text)
        assertEquals("newer helper", session.fileDrafts["one/helper.js"])
    }
    @Test fun failedSwitchKeepsTheSelectedWorkAndItsUndo() = runBlocking {
        val session = session(); val store = Store().apply { succeed = false }; val vm = vm(session, store, this)
        vm.workMenuExpanded = true
        vm.selectWork("two", false)!!.join()
        assertEquals("one", session.activeWorkIdState.value)
        assertEquals("unsaved", session.editorValueState.value.text)
        assertTrue(vm.workMenuExpanded)
        assertEquals(1, session.undoStack.size)
    }
    @Test fun successfulSwitchSavesDraftsBeforeSelectingTheNextWork() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        vm.selectWork("two", true)!!.join()
        assertEquals("two", store.persisted!!.activeWorkId)
        assertEquals("unsaved helper", store.persisted!!.works.first().files["helper.js"])
        assertEquals("two", session.activeWorkIdState.value)
        assertEquals("second saved", session.editorValueState.value.text)
        assertTrue(session.undoStack.isEmpty())
        assertTrue(vm.workActionsMenuExpanded)
    }
    private fun cleanSession() = EditorSessionViewModel().apply {
        initialize(listOf(Work("one", "First", "saved", files = mutableMapOf("helper.js" to "helper")),
            Work("two", "Second", "second saved")), "one")
    }
    @Test fun cleanSwitchSkipsWorkStoreWriteAndPreservesWorkObjects() = runBlocking {
        val session = cleanSession(); val store = Store().apply { succeed = false }
        val vm = vm(session, store, this)
        val works = session.worksState.value
        vm.workMenuExpanded = true
        val switch = vm.selectWork("two", false)!!
        assertFalse(vm.showBlockingProgress)
        switch.join()
        assertEquals(0, store.calls)
        assertSame(works, session.worksState.value)
        assertEquals("two", session.activeWorkIdState.value)
        assertEquals("two", store.selectedId)
        assertFalse(vm.workMenuExpanded)
        assertTrue(vm.events.first().rerun)
    }
    @Test fun selectingCurrentWorkDoesNotWriteOrClearSelectionAndUndo() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        val editor = session.editorValueState.value
        vm.selectWork("one", true)!!.join()
        assertEquals(0, store.calls)
        assertEquals(editor, session.editorValueState.value)
        assertEquals(1, session.undoStack.size)
        assertTrue(vm.workActionsMenuExpanded)
        assertFalse(vm.events.first().rerun)
    }
    @Test fun unchangedAuxiliaryDraftDoesNotRequireWritingAllWorks() = runBlocking {
        val session = cleanSession(); session.fileDrafts["one/helper.js"] = "helper"
        val store = Store(); val vm = vm(session, store, this)
        vm.selectWork("two", false)!!.join()
        assertEquals(0, store.calls)
        assertEquals("helper", session.fileDrafts["one/helper.js"])
    }
    @Test fun cleanSelectionIsRecoveredWithoutAWorkStoreWrite() = runBlocking {
        val original = cleanSession(); val store = Store().apply { persisted = WorkStore(original.worksState.value, "one") }
        vm(original, store, this).selectWork("two", false)!!.join()
        val restored = EditorSessionViewModel()
        vm(restored, store, this).initialize { emptyList() }
        assertEquals("two", restored.activeWorkIdState.value)
        assertEquals("second saved", restored.editorValueState.value.text)
        assertEquals(0, store.calls)
    }
    @Test fun staleRememberedSelectionFallsBackToTheStoredWork() = runBlocking {
        val original = cleanSession()
        val store = Store().apply { persisted = WorkStore(original.worksState.value, "one"); selectedId = "deleted" }
        val restored = EditorSessionViewModel()
        vm(restored, store, this).initialize { emptyList() }
        assertEquals("one", restored.activeWorkIdState.value)
    }
    @Test fun typingDuringSwitchIsSavedBeforeTheEditorIsReplaced() = runBlocking {
        val session = session()
        val store = Store().apply { duringSave = {
            session.editorValueState.value = TextFieldValue("newer typing")
            session.fileDrafts["one/helper.js"] = "newer helper"
        } }
        val vm = vm(session, store, this)
        vm.selectWork("two", false)!!.join()
        assertEquals(2, store.calls)
        assertEquals("newer typing", store.persisted!!.works.first().code)
        assertEquals("newer helper", store.persisted!!.works.first().files["helper.js"])
        assertEquals("two", session.activeWorkIdState.value)
    }
    @Test fun pendingParametersAreSavedBeforeSwitching() = runBlocking {
        val session = cleanSession(); val store = Store(); val vm = vm(session, store, this)
        vm.updateParameter("one", "size", "42")
        vm.selectWork("two", false)!!.join()
        assertEquals(1, store.calls)
        assertEquals("42", store.persisted!!.works.first().parameterValues["size"])
        assertFalse(vm.hasPendingMetadata("one"))
    }
    @Test fun slowFailedSwitchDelaysProgressAndKeepsEdits() = runBlocking {
        val session = session(); val release = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>()
        val store = Store().apply { succeed = false; duringSave = { started.complete(Unit); runBlocking { release.await() } } }
        val vm = WorkManagementViewModel(session, store, io = Dispatchers.IO, operationScope = this)
        vm.workMenuExpanded = true
        val switch = vm.selectWork("two", false)!!
        assertFalse(vm.showBlockingProgress)
        started.await()
        delay(450)
        assertTrue(vm.showBlockingProgress)
        release.complete(Unit)
        switch.join()
        assertFalse(vm.showBlockingProgress)
        assertEquals("one", session.activeWorkIdState.value)
        assertEquals("unsaved", session.editorValueState.value.text)
        assertTrue(vm.workMenuExpanded)
        assertEquals(1, session.undoStack.size)
    }
    @Test fun pinAndTagFailuresDoNotMutateLiveWorks() = runBlocking {
        val session = session(); val store = Store().apply { succeed = false }; val vm = vm(session, store, this)
        val original = session.worksState.value.first()
        vm.togglePin("one")!!.join()
        vm.addTag("one", "test")!!.join()
        assertSame(original, session.worksState.value.first())
        assertFalse(original.isPinned); assertTrue(original.tags.isEmpty())
    }
    @Test fun tagsResolveByIdAfterWorkObjectsAreReplaced() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        vm.editingTagsWorkId = "one"
        vm.addTag("one", "new")!!.join()
        assertEquals(listOf("new"), vm.editingTagsWork!!.tags.toList())
        vm.removeTag("one", "NEW")!!.join()
        assertTrue(vm.editingTagsWork!!.tags.isEmpty())
    }
    @Test fun renameFailureKeepsDialogAndOriginalName() = runBlocking {
        val session = session(); val store = Store().apply { succeed = false }; val vm = vm(session, store, this)
        vm.showRenameDialog = true
        vm.renameWork("one", "Renamed")!!.join()
        assertEquals("First", session.worksState.value.first().title)
        assertTrue(vm.showRenameDialog)
        store.succeed = true
        vm.renameWork("one", "Renamed")!!.join()
        assertEquals("Renamed", session.worksState.value.first().title)
        assertFalse(vm.showRenameDialog)
    }
    @Test fun deleteFailurePreservesWorkAndEditorCaches() = runBlocking {
        val session = session(); val store = Store().apply { succeed = false }; val vm = vm(session, store, this)
        vm.showDeleteDialog = true
        vm.deleteCurrentWork()!!.join()
        assertEquals(2, session.worksState.value.size)
        assertEquals("one", session.activeWorkIdState.value)
        assertEquals("unsaved helper", session.fileDrafts["one/helper.js"])
        assertTrue(vm.showDeleteDialog)
    }
    @Test fun successfulDeleteDiscardsOnlyTheDeletedWorksDrafts() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        session.fileDrafts["two/helper.js"] = "keep"
        vm.deleteCurrentWork()!!.join()
        assertEquals("two", session.activeWorkIdState.value)
        assertFalse(session.fileDrafts.containsKey("one/helper.js"))
        assertEquals("keep", session.fileDrafts["two/helper.js"])
    }
    @Test fun duplicateIncludesUnsavedAuxiliaryFilesAndGetsANewId() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        vm.duplicateWork()!!.join()
        val copy = session.worksState.value.last()
        assertNotEquals("one", copy.id)
        assertEquals(copy.id, session.activeWorkIdState.value)
        assertEquals("unsaved helper", copy.files["helper.js"])
        assertEquals("unsaved", copy.code)
    }
    @Test fun conflictingCommandsAreRejectedWhileBusy() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        val first = vm.saveCurrentWork()!!
        assertNull(vm.deleteCurrentWork())
        first.join()
        assertEquals(1, store.calls)
        assertEquals(2, session.worksState.value.size)
    }
    @Test fun auxiliarySaveFailureKeepsTheUnsavedContent() = runBlocking {
        val session = session(); val store = Store().apply { succeed = false }; val vm = vm(session, store, this)
        vm.saveAuxiliaryFiles(mapOf("other.js" to "new"))!!.join()
        assertEquals("old helper", session.worksState.value.first().files["helper.js"])
        assertEquals("unsaved helper", session.fileDrafts["one/helper.js"])
        assertEquals("unsaved", session.editorValueState.value.text)
    }
    @Test fun runtimeFailureKeepsTheOriginalEnvironment() = runBlocking {
        val session = session(); val store = Store().apply { succeed = false }; val vm = vm(session, store, this)
        val work = session.worksState.value.first()
        vm.showRuntimeDialog = true
        vm.saveRuntime(P5_VERSION_LEGACY, true, mapOf("matter" to "0.20.0"))!!.join()
        assertEquals(P5_VERSION_CURRENT, work.p5Version)
        assertFalse(work.p5SoundEnabled)
        assertTrue(vm.showRuntimeDialog)
    }
    @Test fun addingAFileDoesNotDiscardOtherFilesUnsavedDrafts() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        vm.saveAuxiliaryFiles(mapOf("helper.js" to "old helper", "new.js" to "new file"))!!.join()
        assertEquals("unsaved helper", session.fileDrafts["one/helper.js"])
        assertEquals("old helper", store.persisted!!.works.first().files["helper.js"])
        assertEquals("new file", store.persisted!!.works.first().files["new.js"])
        assertEquals("unsaved", session.lastSavedTextState.value)
    }
    @Test fun changedFileInvalidatesOnlyItsOwnDraftAfterSuccessfulSave() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        session.fileDrafts["two/keep.js"] = "other work"
        vm.saveAuxiliaryFiles(mapOf("helper.js" to "replacement"))!!.join()
        assertFalse(session.fileDrafts.containsKey("one/helper.js"))
        assertEquals("other work", session.fileDrafts["two/keep.js"])
    }
    @Test fun previewRatioFailureRevertsTheTransientRatio() = runBlocking {
        val session = session(); val store = Store().apply { succeed = false }; val vm = vm(session, store, this)
        vm.draftPreviewRatio("16:9")
        assertEquals("16:9", vm.previewRatio(session.worksState.value.first()))
        vm.commitPreviewRatio()!!.join()
        assertEquals("1:1", vm.previewRatio(session.worksState.value.first()))
        assertEquals("1:1", session.worksState.value.first().previewAspectRatio)
    }
    @Test fun failedParameterSaveKeepsARetryableDraftWithoutChangingTheSavedWork() = runBlocking {
        val session = session(); val store = Store().apply { succeed = false }; val vm = vm(session, store, this)
        vm.updateParameter("one", "size", "42")
        vm.flushParameterSave("one").join()
        assertTrue(session.worksState.value.first().parameterValues.isEmpty())
        assertEquals("42", vm.parameterValues(session.worksState.value.first())["size"])
        store.succeed = true
        vm.flushParameterSave("one").join()
        assertEquals("42", session.worksState.value.first().parameterValues["size"])
        assertEquals("unsaved", session.editorValueState.value.text)
    }
    @Test fun exceptionsReleaseTheCommandBusyFlags() = runBlocking {
        val session = session(); val store = Store().apply { duringSave = { error("provider failed") } }
        val vm = vm(session, store, this)
        vm.saveCurrentWork()!!.join()
        assertFalse(vm.workSaving); assertFalse(session.assetBusy); assertFalse(vm.showBlockingProgress)
        assertEquals("unsaved", session.editorValueState.value.text)
    }

    @Test fun samplePromptIsDismissedOnlyAfterSuccessfulPersistence() = runBlocking {
        val session = session(); val store = Store().apply { succeed = false }
        val settingsStore = object : SettingsPersistence {
            var version = 0
            override val samplePromptVersion get() = version
            override fun dismissSamplePrompt(version: Int) { this.version = version }
            override fun load() = SettingsUiState()
            override fun save(state: SettingsUiState) {}
        }
        val settings = SettingsViewModel(settingsStore)
        val vm = WorkManagementViewModel(session, store, settings = settings, io = Dispatchers.Unconfined, operationScope = this)
        val samples = listOf(Work("sample", "Sample", "sample code"))
        vm.addSamples(samples, fromPrompt = true)!!.join()
        assertEquals(2, session.worksState.value.size)
        assertTrue(vm.showSamplePrompt); assertEquals(0, settings.samplePromptVersion)
        store.succeed = true
        vm.addSamples(samples, fromPrompt = true)!!.join()
        assertEquals(3, session.worksState.value.size)
        assertFalse(vm.showSamplePrompt); assertEquals(BuildConfig.VERSION_CODE, settings.samplePromptVersion)
    }

    @Test fun snapshotRestoreFailureKeepsCodeDraftsParametersAndHistory() = runBlocking {
        val session = session(); val store = Store().apply { succeed = false }; val vm = vm(session, store, this)
        vm.updateParameter("one", "size", "42")
        try {
            vm.commitSnapshotRestore(WorkSnapshot(code = "restored", files = mapOf("helper.js" to "restored helper")))
            fail("Expected save failure")
        } catch (_: IllegalStateException) {}
        assertEquals("unsaved", session.editorValueState.value.text)
        assertEquals("unsaved helper", session.fileDrafts["one/helper.js"])
        assertEquals("42", vm.parameterValues(session.worksState.value.first())["size"])
        assertEquals(1, session.undoStack.size)
    }
    @Test fun successfulSnapshotRestoreReplacesCodeAndClearsOnlyItsStaleDrafts() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        vm.updateParameter("one", "size", "42")
        session.fileDrafts["two/keep.js"] = "keep"
        vm.commitSnapshotRestore(WorkSnapshot(code = "restored", files = mapOf("new.js" to "restored helper"),
            parameterValues = mapOf("size" to "10")))
        assertEquals("restored", session.editorValueState.value.text)
        assertEquals("restored", store.persisted!!.works.first().code)
        assertEquals("10", vm.parameterValues(session.worksState.value.first())["size"])
        assertFalse(session.fileDrafts.containsKey("one/helper.js"))
        assertEquals("keep", session.fileDrafts["two/keep.js"])
        assertTrue(session.undoStack.isEmpty())
    }

    @Test fun parameterInsertionSavesMainAndAuxiliaryEditsAndKeepsUndo() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        val before = session.editorValueState.value
        assertTrue(vm.insertParameterDeclarations(PARAMETER_SAMPLE))
        val event = vm.events.first()
        assertTrue(event.rerun); assertTrue(event.forceRun)
        assertEquals(PARAMETER_SAMPLE + before.text, store.persisted!!.works.first().code)
        assertEquals("unsaved helper", store.persisted!!.works.first().files["helper.js"])
        assertEquals(before, session.undo(session.editorValueState.value))
    }
    @Test fun failedParameterSaveKeepsInsertedTextUndoAndPersistedWork() = runBlocking {
        val session = session(); val store = Store().apply { succeed = false }; val vm = vm(session, store, this)
        val before = session.editorValueState.value
        assertTrue(vm.insertParameterDeclarations(PARAMETER_SAMPLE))
        assertTrue(vm.events.first().failure)
        assertTrue(session.editorValueState.value.text.startsWith(PARAMETER_SAMPLE))
        assertEquals("saved", session.worksState.value.first().code)
        assertEquals(before, session.undo(session.editorValueState.value))
    }
    @Test fun parameterInsertionRejectsDuplicatesAcrossAuxiliaryDrafts() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        session.fileDrafts["one/helper.js"] = "// @rin number speed \"Speed\" 0 3 1 0.1"
        val before = session.editorValueState.value
        assertFalse(vm.insertParameterDeclarations(PARAMETER_SAMPLE))
        assertEquals(before, session.editorValueState.value)
        assertEquals(0, store.calls)
    }
    @Test fun physicsWorkPersistsTheRequiredLibraryBeforeSelectingIt() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        vm.createWork("Physics", "1:1", CanvasSizingMode.FIXED,
            WorkTemplate("1:1", 800, 800, WorkTemplateKind.PHYSICS_MATTER))!!.join()
        val work = store.persisted!!.works.last()
        assertEquals(mapOf("matter-js" to "0.20.0"), work.libraries.toMap())
        assertTrue(work.code.contains("Matter.Engine.create()"))
        assertEquals(work.id, session.activeWorkIdState.value)
    }

    @Test fun parameterInsertionRejectsCodeAndDuplicateLinesWithoutChangingTheEditor() = runBlocking {
        val session = session(); val store = Store(); val vm = vm(session, store, this)
        val before = session.editorValueState.value
        assertFalse(vm.insertParameterDeclarations(PARAMETER_SAMPLE + "alert(1);"))
        assertFalse(vm.insertParameterDeclarations(PARAMETER_SAMPLE + PARAMETER_SAMPLE))
        assertEquals(before, session.editorValueState.value)
        assertEquals(0, store.calls)
    }

}
