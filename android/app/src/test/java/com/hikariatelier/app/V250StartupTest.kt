package com.hikariatelier.app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class V250StartupTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Settings : SettingsPersistence {
        var weavePending = true
        var palettePending = true
        var weaveCompletions = 0
        var paletteCompletions = 0
        var state = SettingsUiState()
        override val weaveStartupPending get() = weavePending
        override fun completeWeaveStartup() { weavePending = false; weaveCompletions++ }
        override val paletteStartupPending get() = palettePending
        override fun completePaletteStartup() { palettePending = false; paletteCompletions++ }
        override fun load() = state
        override fun save(state: SettingsUiState) { this.state = state }
    }

    private class Store(var value: WorkStore) : WorkPersistence {
        var remembered: String? = null
        var writes = 0
        var failed = false
        override fun loadLocal() = value
        override fun loadFolder(folderUri: Uri) = value
        override fun selectedWorkId(folderUri: Uri?) = remembered
        override fun rememberSelectedWork(folderUri: Uri?, activeId: String) { remembered = activeId }
        override fun loadResult(folderUri: Uri?, eager: Boolean) = if (failed)
            WorkLoadResult.Failed(WorkLoadFailure.CORRUPT, "damaged")
        else super.loadResult(folderUri, eager)
        override fun save(folderUri: Uri?, works: List<Work>, activeId: String): Boolean {
            writes++
            value = WorkStore(works.map(::snapshotWork), activeId)
            return true
        }
    }

    private fun samples() = listOf(
        Work("halo", "Halo", "halo code"),
        Work("shapes", "Palette", "palette code"),
        Work("wave-parameter", "Weave", "weave code")
    )

    @Test fun freshInstallStartsWeaveAndConsumesBothPendingFlags() = runBlocking {
        val settings = Settings()
        val store = Store(WorkStore(emptyList(), ""))
        val session = EditorSessionViewModel()
        val vm = WorkManagementViewModel(session, store, settings = SettingsViewModel(settings),
            io = Dispatchers.Unconfined, operationScope = this)
        vm.initialize(::samples)
        val weave = vm.officialSamples.first { it.title == "Weave" }
        assertEquals(weave.id, session.activeWorkIdState.value)
        assertEquals("weave code", session.editorValueState.value.text)
        assertTrue(session.sampleReadOnly)
        assertEquals(weave.id, store.remembered)
        assertFalse(settings.weavePending)
        assertEquals(1, settings.weaveCompletions)
        assertFalse(settings.palettePending)
        assertEquals(1, settings.paletteCompletions)
    }

    @Test fun upgradeStartsWeaveOnceAndKeepsUserContentAndLaterSelection() = runBlocking {
        val settings = Settings().apply { palettePending = false }
        val store = Store(WorkStore(listOf(Work("a", "My work", "saved code")), "a"))
        val session = EditorSessionViewModel()
        val vm = WorkManagementViewModel(session, store, settings = SettingsViewModel(settings),
            io = Dispatchers.Unconfined, operationScope = this)
        vm.initialize(::samples)
        val weave = vm.officialSamples.first { it.title == "Weave" }
        assertEquals(weave.id, session.activeWorkIdState.value)
        assertEquals("weave code", session.editorValueState.value.text)
        assertTrue(session.sampleReadOnly)
        assertEquals(weave.id, store.remembered)
        assertEquals("saved code", store.value.works.single().code)
        assertEquals(0, store.writes)
        assertFalse(settings.weavePending)
        assertEquals(1, settings.weaveCompletions)

        vm.selectWork("a", false)!!.join()
        val restarted = EditorSessionViewModel()
        WorkManagementViewModel(restarted, store, settings = SettingsViewModel(settings),
            io = Dispatchers.Unconfined, operationScope = this).initialize(::samples)
        assertEquals("a", restarted.activeWorkIdState.value)
        assertEquals("saved code", restarted.editorValueState.value.text)
        assertEquals(1, settings.weaveCompletions)
    }

    @Test fun failedLoadingOrMissingWeaveKeepsTheStartupRequestForRetry() = runBlocking {
        for (failure in listOf(false, true)) {
            val settings = Settings()
            val store = Store(WorkStore(listOf(Work("a", "My work", "keep")), "a")).apply { failed = failure }
            val session = EditorSessionViewModel()
            val vm = WorkManagementViewModel(session, store, settings = SettingsViewModel(settings),
                io = Dispatchers.Unconfined, operationScope = this)
            vm.initialize { if (failure) samples() else samples().filter { it.id != "wave-parameter" } }
            assertTrue(settings.weavePending)
            assertEquals(0, settings.weaveCompletions)
            assertNull(store.remembered)
            assertEquals("keep", store.value.works.single().code)
            if (failure) {
                assertNotNull(vm.loadFailure)
                assertTrue(session.worksState.value.isEmpty())
            } else assertEquals("a", session.activeWorkIdState.value)
        }
    }

    @Test fun switchingToWeaveKeepsAnUnsavedDraftAvailableForRecovery() = runBlocking {
        val draftFile = File(temporary.newFolder(), "draft.json")
        val drafts = DraftSnapshotRepository(draftFile)
        try {
            val work = Work("a", "My work", "saved", files = mutableMapOf("helper.js" to "helper"))
            drafts.save("a", "unsaved code", mapOf("a/helper.js" to "unsaved helper"),
                draftStoreKey(null), draftBaseHash(work), mapOf("a" to draftBaseHash(work)))
            drafts.awaitPendingWrites()
            val store = Store(WorkStore(listOf(work), "a"))
            val settings = Settings()
            val session = EditorSessionViewModel()
            val vm = WorkManagementViewModel(session, store, drafts = drafts,
                settings = SettingsViewModel(settings), io = Dispatchers.Unconfined, operationScope = this)
            vm.initialize(::samples)
            assertTrue(session.sampleReadOnly)
            assertEquals("unsaved code", vm.pendingDraft!!.first.code)
            assertEquals("unsaved helper", vm.pendingDraft!!.second["a/helper.js"])
            assertEquals("saved", store.value.works.single().code)
            assertEquals("helper", store.value.works.single().files["helper.js"])
            vm.saveDraft()
            drafts.awaitPendingWrites()
            assertTrue(draftFile.exists())
            assertEquals("unsaved code", drafts.load()!!.first.code)
            assertEquals(0, store.writes)
        } finally { drafts.close() }
    }
}
