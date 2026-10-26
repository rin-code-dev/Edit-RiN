package com.hikariatelier.app

import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BlockEditorPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun opaqueMalformedAndFutureStateSurvivesCodecCopiesTemplatesAndZip() {
        for (state in listOf<String?>(null, "{broken", "{\"version\":999,\"future\":[1,2]}")) {
            val work = Work("one", "One", "circle(1, 2, 3);", blockEditorState = state)
            assertEquals(state, parseWorkStoreJson(serializeWorkStore(listOf(work), "one"))!!.works.single().blockEditorState)
            assertEquals(state, snapshotWork(work).blockEditorState)
            assertEquals(state, copyGalleryWork(work, emptySet()).blockEditorState)
            assertEquals(state, newWorkFromUserTemplate(work, "Template").blockEditorState)
            val assets = AssetStorage(temporary.newFolder())
            val bytes = ByteArrayOutputStream()
            writeAssetBackup(bytes, listOf(work), "one", "{}", assets)
            val imported = readAssetBackup(ByteArrayInputStream(bytes.toByteArray()), assets)
            assertEquals(state, imported.store.works.single().blockEditorState)
            assertEquals(state, reidentifyGalleryImport(imported).store.works.single().blockEditorState)
        }
    }

    @Test fun snapshotsIncludeStateAndLegacyMainOnlyRestorePreservesCurrentState() {
        val work = Work("one", "One", "saved", blockEditorState = "layout")
        val content = currentSnapshotContent(work, "new", emptyMap())
        val snapshot = WorkSnapshot(code = content.code, blockEditorState = content.blockEditorState)
        assertEquals(snapshot, decodeSnapshots(encodeSnapshots(listOf(snapshot))).single())
        assertTrue(snapshot.matches(content))
        assertFalse(snapshot.copy(blockEditorState = "other").matches(content))
        assertEquals("old layout", restoredSnapshotWork(work, snapshot.copy(blockEditorState = "old layout"), content).blockEditorState)
        assertEquals("layout", restoredSnapshotWork(work, snapshot.copy(mainCodeOnly = true, blockEditorState = null), content).blockEditorState)
    }

    @Test fun draftsPreserveExplicitNullAndRejectChangedBlockBaseline() {
        val work = Work("one", "One", "saved", blockEditorState = "layout")
        val repository = DraftSnapshotRepository(File(temporary.newFolder(), "draft.json"))
        try {
            repository.save("one", "draft", emptyMap(), "local", draftBaseHash(work),
                blockEditorDrafts = mapOf("one" to null))
            repository.awaitPendingWrites()
            val loaded = repository.loadCompatible("local", listOf(work))!!.first
            assertTrue(loaded.blockEditorDrafts.containsKey("one"))
            assertNull(loaded.blockEditorDrafts["one"])
            val recovered = recoveredDraftWorks(loaded, emptyMap(), listOf(work), true).single()
            assertEquals("draft", recovered.code)
            assertNull(recovered.blockEditorState)
            work.blockEditorState = "externally moved"
            assertNull(repository.loadCompatible("local", listOf(work)))
        } finally { repository.close() }
    }

    private class Documents : WorkDocuments {
        val files = linkedMapOf<String, String>()
        override fun exists(name: String) = name in files
        override fun read(name: String) = files[name]
        override fun write(name: String, content: String): Boolean { files[name] = content; return true }
        override fun rename(from: String, to: String): Boolean {
            if (to in files) return false
            files[to] = files.remove(from) ?: return false
            return true
        }
        override fun delete(name: String) = files.remove(name) != null
    }

    @Test fun splitStoreSavesInPlaceMetadataChangesAndConflictCopiesKeepBothVersions() {
        val documents = Documents()
        val store = SplitWorkStore(documents)
        val work = Work("one", "One", "saved", blockEditorState = "old")
        assertTrue(store.save(listOf(work), "one"))
        work.blockEditorState = "new"
        assertTrue(store.save(listOf(work), "one"))
        val loaded = SplitWorkStore(documents).loadOrMigrate({ null })!!.works.single()
        assertEquals("new", loaded.blockEditorState)
        val original = snapshotWork(loaded)
        val local = snapshotWork(original).apply { blockEditorState = "local" }
        val remote = snapshotWork(original).apply { blockEditorState = "remote" }
        val merged = mergeConcurrentWorks(listOf(original), listOf(local), listOf(remote), "one")
        assertEquals(setOf("local", "remote"), merged.works.map { it.blockEditorState }.toSet())
        assertEquals(1, merged.copies)
    }

    private class Store : WorkPersistence {
        var saved: WorkStore? = null
        var succeed = true
        var duringSave: (() -> Unit)? = null
        override fun loadLocal(): WorkStore? = saved
        override fun loadFolder(folderUri: Uri): WorkStore? = saved
        override fun save(folderUri: Uri?, works: List<Work>, activeId: String): Boolean {
            duringSave?.invoke()
            if (succeed) saved = WorkStore(works.map { snapshotWork(it) }, activeId)
            return succeed
        }
    }

    @Test fun stateOnlySaveCommitsWithoutCodeRevisionAndNewerStateSurvivesAnInflightSave() = runBlocking {
        val session = EditorSessionViewModel().apply { initialize(listOf(Work("one", "One", "saved")), "one") }
        val store = Store()
        val model = WorkManagementViewModel(session, store, io = Dispatchers.Unconfined, operationScope = this, main = Dispatchers.Unconfined)
        assertTrue(session.applyBlockEdit(BlockEditTransaction("one", "saved", emptyList(), "layout")))
        assertTrue(model.hasEditsToSave())
        store.duringSave = { session.applyBlockEdit(BlockEditTransaction("one", "saved", emptyList(), "newer")); Unit }
        model.saveCurrentWork()!!.join()
        assertEquals("layout", store.saved!!.works.single().blockEditorState)
        assertEquals("newer", session.currentBlockEditorState("one"))
        assertTrue(model.hasEditsToSave())
        store.duringSave = null
        model.saveCurrentWork()!!.join()
        assertEquals("newer", store.saved!!.works.single().blockEditorState)
        assertTrue(store.saved!!.works.single().revisions.isEmpty())
        assertFalse(model.hasEditsToSave())
        assertEquals(2, session.undoStack.size)
    }

    @Test fun undoToOldSavedMetadataDuringSaveSurvivesTheNewBaseline() = runBlocking {
        val session = EditorSessionViewModel().apply {
            initialize(listOf(Work("one", "One", "saved", blockEditorState = "original")), "one")
        }
        val store = Store()
        val model = WorkManagementViewModel(session, store, io = Dispatchers.Unconfined, operationScope = this, main = Dispatchers.Unconfined)
        session.applyBlockEdit(BlockEditTransaction("one", "saved", emptyList(), "layout"))
        store.duringSave = { session.editorValueState.value = checkNotNull(session.undo(session.editorValueState.value)) }
        model.saveCurrentWork()!!.join()
        assertEquals("layout", store.saved!!.works.single().blockEditorState)
        assertEquals("original", session.currentBlockEditorState("one"))
        assertEquals("original", session.blockEditorDrafts["one"])
        assertTrue(model.hasEditsToSave())
    }

    @Test fun codeDraftCapturesUnchangedActiveMetadataForStaleBaseRecovery() = runBlocking {
        val work = Work("one", "One", "saved", blockEditorState = "original layout")
        val session = EditorSessionViewModel().apply { initialize(listOf(work), "one") }
        val repository = DraftSnapshotRepository(File(temporary.newFolder(), "draft.json"))
        try {
            val model = WorkManagementViewModel(session, Store(), drafts = repository,
                io = Dispatchers.Unconfined, operationScope = this, main = Dispatchers.Unconfined)
            session.editorValueState.value = androidx.compose.ui.text.input.TextFieldValue("unsaved code")
            assertTrue(session.blockEditorDrafts.isEmpty())
            model.saveDraft()
            model.awaitDraftSave()
            val draft = checkNotNull(repository.load()).first
            assertEquals("original layout", draft.blockEditorDrafts["one"])
            work.blockEditorState = "external layout"
            val recovered = recoveredDraftWorks(draft, emptyMap(), listOf(work), true).single()
            assertEquals("unsaved code", recovered.code)
            assertEquals("original layout", recovered.blockEditorState)
        } finally { repository.close() }
    }

    @Test fun saveFlushesPendingBlockChangesBeforeCaptureAndBeforeLocking() = runBlocking {
        val session = EditorSessionViewModel().apply { initialize(listOf(Work("one", "One", "saved")), "one") }
        val store = Store()
        val model = WorkManagementViewModel(session, store, io = Dispatchers.Unconfined, operationScope = this, main = Dispatchers.Unconfined)
        session.blockEditorFlusher = {
            assertFalse(session.editorInputLocked)
            assertTrue(session.applyBlockEdit(BlockEditTransaction("one", "saved", listOf(SourcePatch(0, 5, "pending")), "layout")))
            true
        }
        model.saveCurrentWork(blockUi = true)!!.join()
        assertEquals("pending", store.saved!!.works.single().code)
        assertEquals("layout", store.saved!!.works.single().blockEditorState)
        assertFalse(model.hasEditsToSave())
    }

    @Test fun rejectedFlushPreventsSaveAndNavigationWithoutChangingState() = runBlocking {
        val session = EditorSessionViewModel().apply {
            initialize(listOf(Work("one", "One", "saved"), Work("two", "Two", "other")), "one")
        }
        val store = Store()
        val model = WorkManagementViewModel(session, store, io = Dispatchers.Unconfined, operationScope = this, main = Dispatchers.Unconfined)
        session.blockEditorFlusher = { false }
        model.saveCurrentWork()!!.join()
        model.selectWork("two", false)!!.join()
        assertNull(store.saved)
        assertEquals("one", session.activeWorkIdState.value)
        assertEquals("saved", session.editorValueState.value.text)
        assertFalse(session.editorInputLocked)
        assertFalse(session.assetBusy)
    }

    @Test fun draftWaitsForPendingBlockCommitAndDoesNotCaptureADifferentWork() = runBlocking {
        val work = Work("one", "One", "saved")
        val session = EditorSessionViewModel().apply { initialize(listOf(work, Work("two", "Two", "other")), "one") }
        val repository = DraftSnapshotRepository(File(temporary.newFolder(), "draft.json"))
        try {
            val model = WorkManagementViewModel(session, Store(), drafts = repository,
                io = Dispatchers.Unconfined, operationScope = this, main = Dispatchers.Unconfined)
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            session.blockEditorFlusher = { release.await(); true }
            model.saveDraft()
            session.activeWorkIdState.value = "two"
            release.complete(Unit)
            model.awaitDraftSave()
            assertNull(repository.load())
        } finally { repository.close() }
    }

    @Test fun openInputDraftReachesRecoveryStorageWithoutCommittingTheWork() = runBlocking {
        val work = Work("one", "One", "circle(1, 2, 3);", blockEditorState = "layout")
        val session = EditorSessionViewModel().apply { initialize(listOf(work), "one") }
        val repository = DraftSnapshotRepository(File(temporary.newFolder(), "draft.json"))
        try {
            val model = WorkManagementViewModel(session, Store(), drafts = repository,
                io = Dispatchers.Unconfined, operationScope = this, main = Dispatchers.Unconfined)
            session.notifyBlockInputChanged()
            session.blockEditorFlusher = { hideChaff ->
                assertFalse(hideChaff)
                session.stageBlockInputDraft(BlockEditTransaction("one", work.code,
                    listOf(SourcePatch(7, 8, "123")), "input layout", session.currentBlockDocumentRevision("one")))
            }
            model.saveDraft()
            model.awaitDraftSave()
            val loaded = checkNotNull(repository.loadCompatible("local", listOf(work))).first
            assertEquals("circle(123, 2, 3);", loaded.code)
            assertEquals("input layout", loaded.blockEditorDrafts["one"])
            val recovered = recoveredDraftWorks(loaded, emptyMap(), listOf(work), true).single()
            assertEquals(loaded.code, recovered.code)
            assertEquals("input layout", recovered.blockEditorState)
            assertEquals(work.code, session.editorValueState.value.text)
            assertEquals("layout", session.currentBlockEditorState("one"))
            assertTrue(session.undoStack.isEmpty())
            assertTrue(session.redoStack.isEmpty())
            // A temporarily incomplete field retains the last validated recovery state.
            session.notifyBlockInputChanged()
            session.blockEditorFlusher = { false }
            model.saveDraft()
            model.awaitDraftSave()
            assertEquals(loaded.code, repository.loadCompatible("local", listOf(work))!!.first.code)
            // Cancelling the field must replace the recovery draft with the acknowledged code.
            session.clearBlockInputDraft()
            session.blockEditorFlusher = { true }
            model.saveDraft()
            model.awaitDraftSave()
            assertEquals(work.code, repository.loadCompatible("local", listOf(work))!!.first.code)
        } finally { repository.close() }
    }

    @Test fun saveFailureRetainsBlockDraftAndUndo() = runBlocking {
        val session = EditorSessionViewModel().apply { initialize(listOf(Work("one", "One", "saved")), "one") }
        val store = Store().apply { succeed = false }
        val model = WorkManagementViewModel(session, store, io = Dispatchers.Unconfined, operationScope = this, main = Dispatchers.Unconfined)
        session.applyBlockEdit(BlockEditTransaction("one", "saved", emptyList(), "layout"))
        model.saveCurrentWork()!!.join()
        assertEquals("layout", session.currentBlockEditorState("one"))
        assertNull(session.worksState.value.single().blockEditorState)
        assertEquals(1, session.undoStack.size)
        assertTrue(model.hasEditsToSave())
    }
}
