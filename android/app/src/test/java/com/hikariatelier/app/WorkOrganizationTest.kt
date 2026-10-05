package com.hikariatelier.app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkOrganizationTest {
    private class Store(var value: WorkStore? = null) : WorkPersistence {
        var fail = false
        var writes = 0
        override fun loadLocal() = value
        override fun loadFolder(folderUri: Uri) = value
        override fun save(folderUri: Uri?, works: List<Work>, activeId: String): Boolean {
            writes++
            if (fail) return false
            value = WorkStore(works.map(::snapshotWork), activeId)
            return true
        }
    }

    @Test fun savedNamesAndIdsRemainUserWorksAndOriginalsNeverEnterTheSavedStore() = runBlocking {
        val saved = listOf(Work("halo", "Halo", "my edits"),
            Work("builtin-sample:halo", "Halo", "another work"),
            Work("gravity", "Gravity", "my gravity", p5Version = P5_VERSION_CURRENT))
        val store = Store(WorkStore(saved, "halo"))
        val session = EditorSessionViewModel()
        val vm = WorkManagementViewModel(session, store, io = Dispatchers.Unconfined, operationScope = this)
        vm.initialize { listOf(Work("halo", "Halo", "original")) }
        assertEquals(3, session.worksState.value.count { !it.isSample })
        val sample = vm.officialSamples.single()
        assertNotEquals("halo", sample.id)
        assertNotEquals("builtin-sample:halo", sample.id)
        assertEquals("my edits", session.editorValueState.value.text)
        assertEquals(P5_VERSION_CURRENT, session.worksState.value.first { it.id == "gravity" }.p5Version)
        vm.selectWork(sample.id, false)!!.join()
        vm.renameWork(sample.id, "overwrite")!!.join()
        vm.deleteGalleryWorks(setOf(sample.id))!!.join()
        vm.saveRuntime(P5_VERSION_LEGACY, false, emptyMap())!!.join()
        assertEquals(0, store.writes)
        assertEquals("original", sample.code)
        vm.createWork("New", "1:1", CanvasSizingMode.FIXED, WorkTemplate("1:1", 800, 800))!!.join()
        assertTrue(store.value!!.works.none { it.isSample })
        assertEquals(saved.map { it.id }, store.value!!.works.take(3).map { it.id })
        assertEquals(saved.map { it.code }, store.value!!.works.take(3).map { it.code })
    }

    @Test fun copyingRetainsTrialParametersAndOnlySwitchesAfterDurableSave() = runBlocking {
        val store = Store()
        val session = EditorSessionViewModel()
        val vm = WorkManagementViewModel(session, store, io = Dispatchers.Unconfined, operationScope = this)
        vm.initialize { listOf(Work("sound", "Sound", "original", files = mutableMapOf("helper.js" to "helper"),
            p5Version = P5_VERSION_CURRENT, p5SoundEnabled = true, libraries = mapOf("matter-js" to "0.20.0"))) }
        val sample = vm.officialSamples.single()
        vm.updateParameter(sample.id, "speed", "2")
        vm.scheduleParameterSave(sample.id)
        assertFalse(vm.hasEditsToSave())
        assertFalse(vm.hasPendingMetadata(sample.id))
        assertEquals(0, store.writes)
        vm.requestSampleCopy(sample.id)
        store.fail = true
        vm.copySample("My synth", "")!!.join()
        assertEquals(sample.id, session.activeWorkIdState.value)
        assertEquals(sample.id, vm.copySampleId)
        assertEquals(1, session.worksState.value.size)
        store.fail = false
        vm.copySample("My synth", "")!!.join()
        val copy = store.value!!.works.single()
        assertFalse(copy.isSample)
        assertNotEquals(sample.id, copy.id)
        assertEquals("My synth", copy.title)
        assertEquals(sample.code, copy.code)
        assertEquals(sample.files.toMap(), copy.files.toMap())
        assertEquals(sample.libraries, copy.libraries)
        assertTrue(copy.p5SoundEnabled)
        assertEquals("2", copy.parameterValues["speed"])
        assertTrue(sample.parameterValues.isEmpty())
        copy.files["helper.js"] = "edited"
        assertEquals("helper", sample.files["helper.js"])
        assertEquals(copy.id, session.activeWorkIdState.value)
        assertNull(vm.copySampleId)
    }

    @Test fun folderMovesSurviveLazyStoreReloadAndFolderDeletionKeepsWorks() = runBlocking {
        val files = mutableMapOf<String, String>()
        val docs = object : WorkDocuments {
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
        val initial = listOf(Work("a", "First", "first"), Work("b", "Second", "second"))
        val split = SplitWorkStore(docs)
        assertTrue(split.save(initial, "a"))
        val loaded = SplitWorkStore(docs)
        val store = object : WorkPersistence {
            override fun loadLocal() = loaded.load(eager = false)
            override fun loadFolder(folderUri: Uri) = loadLocal()
            override fun loadWork(folderUri: Uri?, workId: String) = loaded.loadWork(workId)
            override fun save(folderUri: Uri?, works: List<Work>, activeId: String) = loaded.save(works, activeId)
        }
        val session = EditorSessionViewModel()
        val vm = WorkManagementViewModel(session, store, io = Dispatchers.Unconfined, operationScope = this)
        vm.initialize { listOf(Work("halo", "Halo", "original")) }
        vm.createGalleryFolder("Study")!!.join()
        vm.moveGalleryWorks(setOf("b"), "Study")!!.join()
        val reloaded = SplitWorkStore(docs).load(eager = false)!!
        assertEquals("Study", reloaded.works.first { it.id == "b" }.folderName)
        assertFalse(reloaded.works.first { it.id == "b" }.bodyLoaded)
        assertEquals("second", SplitWorkStore(docs).load()!!.works.first { it.id == "b" }.code)
        vm.renameGalleryFolder("Study", "Practice")!!.join()
        assertEquals("Practice", SplitWorkStore(docs).load()!!.works.first { it.id == "b" }.folderName)
        vm.deleteGalleryFolder("Practice")!!.join()
        val users = SplitWorkStore(docs).load()!!.works
        assertEquals(2, users.size)
        assertEquals(initial.map { it.code }, users.map { it.code })
        assertTrue(users.all { it.folderName.isEmpty() })
        assertEquals("original", vm.officialSamples.single().code)
    }

    @Test fun tabAndFolderReorderingRetainsCustomOrderAcrossMutations() = runBlocking {
        val store = Store(WorkStore(listOf(Work("1", "One", "c1")), "1"))
        val session = EditorSessionViewModel()
        val vm = WorkManagementViewModel(session, store, io = Dispatchers.Unconfined, operationScope = this)
        vm.initialize { listOf(Work("sample", "Sample", "sample code")) }
        vm.createGalleryFolder("FolderA")!!.join()
        vm.createGalleryFolder("FolderB")!!.join()
        vm.createGalleryFolder("FolderC")!!.join()
        assertEquals(listOf("FolderA", "FolderB", "FolderC"), vm.galleryFolders)
        assertTrue(SAMPLE_FOLDER in vm.galleryTabs)

        // Custom swap / reorder
        val reordered = listOf(SAMPLE_FOLDER, "FolderC", "FolderA", "", "FolderB")
        vm.reorderGalleryTabs(reordered)!!.join()
        assertEquals(listOf("FolderC", "FolderA", "FolderB"), vm.galleryFolders)
        assertEquals(reordered, vm.galleryTabs)

        // Renaming preserves position
        vm.renameGalleryFolder("FolderA", "FolderAlpha")!!.join()
        assertEquals(listOf("FolderC", "FolderAlpha", "FolderB"), vm.galleryFolders)
        assertEquals(listOf(SAMPLE_FOLDER, "FolderC", "FolderAlpha", "", "FolderB"), vm.galleryTabs)

        // Deleting removes target while keeping remaining order
        vm.deleteGalleryFolder("FolderC")!!.join()
        assertEquals(listOf("FolderAlpha", "FolderB"), vm.galleryFolders)
        assertEquals(listOf(SAMPLE_FOLDER, "FolderAlpha", "", "FolderB"), vm.galleryTabs)
    }
}
