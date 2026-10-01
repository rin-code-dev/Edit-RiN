package com.hikariatelier.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SplitWorkStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private class Documents : WorkDocuments {
        val files = linkedMapOf<String, String>()
        val writes = mutableListOf<Pair<String, Int>>()
        val reads = mutableListOf<String>()
        var failWrite: (String) -> Boolean = { false }
        var failRename: (String, String) -> Boolean = { _, _ -> false }
        var throwAfterPromotion = false
        var afterRename: ((String, String) -> Unit)? = null
        var truncateIndex = false
        var afterWrite: ((String, String) -> Unit)? = null
        override fun exists(name: String) = name in files
        override fun read(name: String): String? {
            reads += name
            if (throwAfterPromotion && name == "index.json" && files.containsKey("index.json.backup")) {
                throwAfterPromotion = false
                throw IllegalStateException("Read failed")
            }
            return files[name]
        }
        override fun write(name: String, content: String): Boolean {
            writes += name to content.toByteArray().size
            if (failWrite(name)) return false
            files[name] = if (truncateIndex && name == "index.json.pending") content.dropLast(1) else content
            afterWrite?.invoke(name, content)
            return true
        }
        override fun rename(from: String, to: String): Boolean {
            if (failRename(from, to) || to in files) return false
            files[to] = files.remove(from) ?: return false
            afterRename?.invoke(from, to)
            return true
        }
        override fun delete(name: String) = files.remove(name) != null
        fun resetCounts() { writes.clear(); reads.clear() }
        fun bodyNames() = files.keys.filter { it.matches(Regex("[a-f0-9]{64}-[a-f0-9]{64}\\.json")) }
    }
    private fun works() = listOf(Work("a", "A", "first", files = mutableMapOf("helper.js" to "helper")),
        Work("b", "B", "second"))
    private fun paths(json: String): List<String> = JSONObject(json).getJSONArray("works").let { array ->
        List(array.length()) { array.getJSONObject(it).getString("file") }
    }

    @Test fun changingOneLargeWorkWritesOneBodyAndSmallIndexWithoutReadingOtherBodies() {
        val docs = Documents(); val store = SplitWorkStore(docs)
        val original = List(100) { Work("work-$it", "Work $it", "x".repeat(20_000)) }
        assertTrue(store.save(original, original.first().id))
        val firstPaths = paths(docs.files.getValue("index.json"))
        docs.resetCounts()
        val updated = original.mapIndexed { i, work -> snapshotWork(work).apply { if (i == 50) code = "y".repeat(20_000) } }
        assertTrue(store.save(updated, original.first().id))
        val afterPaths = paths(docs.files.getValue("index.json"))
        assertEquals(1, firstPaths.zip(afterPaths).count { it.first != it.second })
        assertEquals(1, docs.writes.count { !it.first.startsWith("index.") })
        assertEquals(2, docs.writes.size)
        assertTrue(docs.reads.filterNot { it.startsWith("index.") }.all { it == afterPaths[50] })
        val legacyBytes = serializeWorkStore(updated, original.first().id).toByteArray().size
        val writtenBytes = docs.writes.sumOf { it.second }
        assertTrue("split=$writtenBytes legacy=$legacyBytes", writtenBytes * 10 < legacyBytes)
        println("100 works / 20KB each: split writes=$writtenBytes bytes; legacy JSON=$legacyBytes bytes")
    }

    @Test fun unchangedCopiesDoNotWriteAndInPlaceEditsAreDetected() {
        val docs = Documents(); val store = SplitWorkStore(docs); val original = works()
        assertTrue(store.save(original, "a")); docs.resetCounts()
        assertTrue(store.save(original.map(::snapshotWork), "a"))
        assertTrue(docs.writes.isEmpty())
        original.first().files["helper.js"] = "changed helper"
        assertTrue(store.save(original, "a"))
        assertEquals(1, docs.writes.count { !it.first.startsWith("index.") })
        assertEquals("changed helper", SplitWorkStore(docs).load()!!.works.first().files["helper.js"])
    }

    @Test fun selectionChangesWriteOnlyIndexAndDeletionKeepsConsistentBackup() {
        val docs = Documents(); val store = SplitWorkStore(docs); val original = works()
        assertTrue(store.save(original, "a")); docs.resetCounts()
        assertTrue(store.save(original, "b"))
        assertEquals(listOf("index.json.pending"), docs.writes.map { it.first })
        assertTrue(store.save(listOf(original.last()), "b"))
        assertEquals(listOf("b"), SplitWorkStore(docs).load()!!.works.map { it.id })
        docs.files["index.json"] = "broken"
        val fallback = SplitWorkStore(docs).load()!!
        assertEquals(listOf("a", "b"), fallback.works.map { it.id })
        assertEquals("b", fallback.activeWorkId)
    }

    @Test fun bodyWriteFailureAndTruncatedIndexKeepOriginalAndPermitRetry() {
        for (mode in listOf("body", "index")) {
            val docs = Documents(); val store = SplitWorkStore(docs); val original = works()
            assertTrue(store.save(original, "a"))
            val filesBefore = docs.files.toMap()
            docs.failWrite = { mode == "body" && !it.startsWith("index.") }
            docs.truncateIndex = mode == "index"
            val next = original.map(::snapshotWork).also { it.first().code = "new" }
            assertFalse(store.save(next, "a"))
            assertEquals(filesBefore, docs.files)
            assertEquals("first", SplitWorkStore(docs).load()!!.works.first().code)
            docs.failWrite = { false }; docs.truncateIndex = false
            assertTrue(store.save(next, "a"))
            assertEquals("new", SplitWorkStore(docs).load()!!.works.first().code)
        }
    }

    @Test fun promotionFailureAndReadBackExceptionRollBackTheEntireGeneration() {
        for (mode in listOf("rename", "read")) {
            val docs = Documents(); val store = SplitWorkStore(docs); val original = works()
            assertTrue(store.save(original, "a"))
            docs.failRename = { from, _ -> mode == "rename" && from == "index.json.pending" }
            docs.afterRename = { from, _ -> if (mode == "read" && from == "index.json.pending") docs.throwAfterPromotion = true }
            val next = original.map(::snapshotWork).also { it.first().code = "new first"; it.last().code = "new second" }
            assertFalse(store.save(next, "b"))
            val recovered = SplitWorkStore(docs).load()!!
            assertEquals(listOf("first", "second"), recovered.works.map { it.code })
            assertEquals("a", recovered.activeWorkId)
        }
    }

    @Test fun interruptedCommitRecoversAllOldWorksThenCanSaveAgain() {
        val docs = Documents(); val store = SplitWorkStore(docs); val original = works()
        assertTrue(store.save(original, "a"))
        var crashState: Map<String, String>? = null
        docs.afterRename = { from, _ -> if (from == "index.json") crashState = docs.files.toMap() }
        assertTrue(store.save(original.map(::snapshotWork).also { it.first().code = "new" }, "b"))
        val restartedDocs = Documents().apply { files.putAll(crashState!!) }
        val restarted = SplitWorkStore(restartedDocs)
        assertEquals(listOf("first", "second"), restarted.load()!!.works.map { it.code })
        assertTrue(restarted.save(original.map(::snapshotWork).also { it.last().code = "retry" }, "b"))
        assertEquals(listOf("first", "retry"), SplitWorkStore(restartedDocs).load()!!.works.map { it.code })
    }

    @Test fun missingOrDamagedWorkFallsBackToCompletePreviousGeneration() {
        for (damage in listOf("missing", "truncated")) {
            val docs = Documents(); val store = SplitWorkStore(docs); val original = works()
            assertTrue(store.save(original, "a"))
            assertTrue(store.save(original.map(::snapshotWork).also { it.first().code = "new" }, "b"))
            val changed = paths(docs.files.getValue("index.json")).first()
            if (damage == "missing") docs.files.remove(changed) else docs.files[changed] = "bad"
            val recovered = SplitWorkStore(docs)
            assertEquals(listOf("first", "second"), recovered.load()!!.works.map { it.code })
            assertTrue(recovered.save(original.map(::snapshotWork).also { it.last().code = "after recovery" }, "a"))
            assertEquals("after recovery", SplitWorkStore(docs).load()!!.works.last().code)
        }
    }

    @Test fun unreadableCommittedStoreCannotBeReplacedWithEmptyDataOrLegacyData() {
        val docs = Documents().apply { files["index.json"] = "broken" }
        val store = SplitWorkStore(docs)
        assertTrue(runCatching { store.loadOrMigrate({ WorkStore(works(), "a") }) }.isFailure)
        assertFalse(store.save(emptyList(), ""))
        assertEquals("broken", docs.files["index.json"])
    }

    @Test fun legacyMigrationKeepsOriginalAndRetriesAfterFailure() {
        val legacyDocs = Documents().apply { files["works.json"] = serializeWorkStore(works(), "b") }
        val before = legacyDocs.files.toMap()
        val docs = Documents().apply { failWrite = { !it.startsWith("index.") } }
        val store = SplitWorkStore(docs)
        val legacy = { loadWorkDocument(legacyDocs, "works.json") }
        assertEquals("second", store.loadOrMigrate(legacy)!!.works.last().code)
        assertFalse(docs.exists("index.json"))
        docs.failWrite = { false }
        assertEquals("b", store.loadOrMigrate(legacy)!!.activeWorkId)
        assertEquals(before, legacyDocs.files)
        val reopened = SplitWorkStore(docs).loadOrMigrate({ error("Must not read old format after migration") })!!
        assertEquals(listOf("a", "b"), reopened.works.map { it.id })
    }

    @Test fun expiredBodiesAreRemovedButCurrentAndBackupStayReadable() {
        val docs = Documents(); val store = SplitWorkStore(docs); val work = Work("a", "A", "one")
        for (code in listOf("one", "two", "three", "four")) {
            work.code = code
            assertTrue(store.save(listOf(work), "a"))
            assertTrue(docs.bodyNames().size <= 2)
        }
        assertEquals("four", SplitWorkStore(docs).load()!!.works.single().code)
        docs.files.remove("index.json")
        assertEquals("three", SplitWorkStore(docs).load()!!.works.single().code)
    }

    @Test fun importedIdsNeverBecomePathsAndInvalidManifestCannotEscapeStore() {
        val docs = Documents(); val store = SplitWorkStore(docs)
        val id = "../作品 / with spaces"
        assertTrue(store.save(listOf(Work(id, "日本語", "code")), id))
        assertEquals(id, SplitWorkStore(docs).load()!!.works.single().id)
        val manifest = JSONObject(docs.files.getValue("index.json"))
        manifest.getJSONArray("works").getJSONObject(0).put("file", "../secret.json")
        docs.files["index.json"] = manifest.toString()
        docs.reads.clear()
        assertTrue(runCatching { SplitWorkStore(docs).load() }.isFailure)
        assertFalse("../secret.json" in docs.reads)
    }

    @Test fun detectsExternalIndexUpdatesInsteadOfOverwritingThem() {
        val docs = Documents(); val first = SplitWorkStore(docs); val original = works()
        assertTrue(first.save(original, "a"))
        val second = SplitWorkStore(docs); second.load()
        assertTrue(second.save(original.map(::snapshotWork).also { it.last().code = "external" }, "b"))
        docs.resetCounts()
        assertFalse(first.save(original.map(::snapshotWork).also { it.first().code = "stale edit" }, "a"))
        assertTrue(docs.writes.isEmpty())
        assertEquals("external", SplitWorkStore(docs).load()!!.works.last().code)
    }

    @Test fun backupAssetsStayProtectedAndOnlyChangedAssetSetsNeedFolderSynchronization() {
        val docs = Documents(); val store = SplitWorkStore(docs)
        val asset = ProjectAsset("0".repeat(64), 1, "image/png")
        val original = listOf(Work("a", "A", "code", assets = mapOf("x.png" to asset)))
        assertEquals(1, store.worksWithChangedAssets(original).size)
        assertTrue(store.save(original, "a"))
        val next = original.map(::snapshotWork).also { it.single().code = "new code" }
        assertTrue(store.worksWithChangedAssets(next).isEmpty())
        next.single().assets.clear()
        assertTrue(store.save(next, "a"))
        assertEquals(setOf(asset.hash), store.retainedAssetHashes())
        assertTrue(store.save(next.map(::snapshotWork).also { it.single().code = "third" }, "a"))
        assertTrue(store.retainedAssetHashes().isEmpty())
    }

    @Test fun restartRoundTripKeepsAssetsParametersRuntimeAndBackupCompatibility() {
        val docs = Documents(); val work = Work("a", "作品", "main", files = mutableMapOf("x.frag" to "shader"),
            assets = mapOf("image.png" to ProjectAsset("1".repeat(64), 10, "image/png")),
            previewAspectRatio = "9:16", p5Version = P5_VERSION_LEGACY, p5SoundEnabled = true,
            libraries = mapOf("matter-js" to "0.20.0"), parameterValues = mapOf("speed" to "2"),
            createdAt = 123, updatedAt = 456, isPinned = true, tags = listOf("art"))
        assertTrue(SplitWorkStore(docs).save(listOf(work), "a"))
        val loaded = SplitWorkStore(docs).load()!!
        val exported = parseWorkStoreJson(serializeWorkStore(loaded.works, loaded.activeWorkId))!!
        val result = exported.works.single()
        assertEquals(work.id, result.id); assertEquals(work.title, result.title); assertEquals(work.code, result.code)
        assertEquals(work.files.toMap(), result.files.toMap()); assertEquals(work.assets.toMap(), result.assets.toMap())
        assertEquals(work.libraries, result.libraries); assertEquals(work.parameterValues.toMap(), result.parameterValues.toMap())
        assertEquals("9:16", result.previewAspectRatio); assertEquals(P5_VERSION_LEGACY, result.p5Version)
        assertTrue(result.p5SoundEnabled); assertTrue(result.isPinned); assertEquals(listOf("art"), result.tags.toList())
        assertEquals(123, result.createdAt); assertEquals(456, result.updatedAt)
    }
    @Test fun filesystemDocumentsSurviveReopeningAndRecoverDamagedIndex() {
        val root = temporary.newFolder()
        val original = works()
        val store = SplitWorkStore(FileWorkDocuments(root))
        assertTrue(store.save(original, "a"))
        val firstIndex = File(root, "index.json").readText()
        val unchangedFile = File(root, paths(firstIndex).last())
        val unchangedTime = unchangedFile.lastModified()
        val unchangedContents = unchangedFile.readText()
        assertTrue(store.save(original.map(::snapshotWork).also { it.first().code = "updated" }, "b"))
        assertEquals(unchangedTime, unchangedFile.lastModified())
        assertEquals(unchangedContents, unchangedFile.readText())
        assertEquals("updated", SplitWorkStore(FileWorkDocuments(root)).load()!!.works.first().code)
        File(root, "index.json").writeText("broken")
        val recovered = SplitWorkStore(FileWorkDocuments(root))
        assertEquals(listOf("first", "second"), recovered.load()!!.works.map { it.code })
        assertTrue(recovered.save(original, "a"))
        assertEquals("a", SplitWorkStore(FileWorkDocuments(root)).load()!!.activeWorkId)
    }

    @Test fun failedSplitPersistenceKeepsViewModelSelectionUndoAndUnsavedFiles() = runBlocking {
        val docs = Documents(); val engine = SplitWorkStore(docs); val original = works()
        assertTrue(engine.save(original, "a"))
        val persistence = object : WorkPersistence {
            override fun loadLocal() = engine.load()
            override fun loadFolder(folderUri: Uri) = engine.load()
            override fun save(folderUri: Uri?, works: List<Work>, activeId: String) = engine.save(works, activeId)
        }
        val session = EditorSessionViewModel().apply {
            initialize(original, "a")
            editorValueState.value = TextFieldValue("unsaved edit", TextRange(3))
            fileDrafts["a/helper.js"] = "unsaved helper"
            undoStack.add(TextFieldValue("before"))
        }
        val vm = WorkManagementViewModel(session, persistence, io = Dispatchers.Unconfined, operationScope = this)
        docs.failRename = { from, _ -> from == "index.json.pending" }
        vm.saveCurrentWork()!!.join()
        assertEquals("a", session.activeWorkIdState.value)
        assertEquals(TextFieldValue("unsaved edit", TextRange(3)), session.editorValueState.value)
        assertEquals("first", session.lastSavedTextState.value)
        assertEquals("unsaved helper", session.fileDrafts["a/helper.js"])
        assertEquals(1, session.undoStack.size)
        assertEquals("first", SplitWorkStore(docs).load()!!.works.first().code)
        docs.failRename = { _, _ -> false }
        vm.saveCurrentWork()!!.join()
        val saved = SplitWorkStore(docs).load()!!.works.first()
        assertEquals("unsaved edit", saved.code)
        assertEquals("unsaved helper", saved.files["helper.js"])
        assertEquals(1, session.undoStack.size)
    }

    @Test fun splitWorksExportAndImportThroughExistingZipWithBinaryAssets() {
        val source = AssetStorage(temporary.newFolder())
        val bytes = byteArrayOf(0, 1, 2, 127, -1)
        val asset = source.put(ByteArrayInputStream(bytes), "image/png")
        val work = Work("a", "A", "loadImage('image.png')", assets = mapOf("image.png" to asset))
        val docs = Documents()
        assertTrue(SplitWorkStore(docs).save(listOf(work), "a"))
        val loaded = SplitWorkStore(docs).load()!!
        val zip = ByteArrayOutputStream()
        writeAssetBackup(zip, loaded.works, loaded.activeWorkId, "{}", source)
        val target = AssetStorage(temporary.newFolder())
        val restored = readAssetBackup(ByteArrayInputStream(zip.toByteArray()), target)
        assertEquals(work.code, restored.store.works.single().code)
        assertArrayEquals(bytes, target.file(asset).readBytes())
        val newDocs = Documents()
        assertTrue(SplitWorkStore(newDocs).save(restored.store.works, restored.store.activeWorkId))
        assertEquals(asset, SplitWorkStore(newDocs).load()!!.works.single().assets["image.png"])
    }
    @Test fun conflictRetriesNeverAcknowledgeExternalGenerationOrOverwriteOtherWorks() {
        val docs = Documents(); val first = SplitWorkStore(docs); val original = works()
        assertTrue(first.save(original, "a"))
        val external = SplitWorkStore(docs)
        external.load()
        val changed = original.map(::snapshotWork).also { it.last().code = "externally updated B" }
        assertTrue(external.save(changed, "b"))
        val local = original.map(::snapshotWork).also { it.first().code = "local A" }
        docs.resetCounts()
        repeat(3) { assertEquals(WorkSaveResult.Conflict, first.saveResult(local, "a")) }
        assertTrue(docs.writes.isEmpty())
        assertEquals("externally updated B", SplitWorkStore(docs).load()!!.works.last().code)
        val latest = first.load()!!.works.map(::snapshotWork).also { it.first().code = "local A" }
        assertEquals(WorkSaveResult.Saved, first.saveResult(latest, "a"))
        assertEquals(listOf("local A", "externally updated B"), SplitWorkStore(docs).load()!!.works.map { it.code })
    }

    @Test fun lazyStartupReadsSelectedBodyOnlyAndRetainsGallerySummaries() {
        val docs = Documents()
        val original = List(100) { Work("w-$it", "Work $it", "source-$it",
            files = mutableMapOf("helper.js" to "helper-$it", "shader.frag" to "shader-$it"),
            previewAspectRatio = "9:16", libraries = mapOf("matter-js" to "0.20.0")) }
        assertTrue(SplitWorkStore(docs).save(original, "w-0"))
        docs.resetCounts()
        val reader = SplitWorkStore(docs)
        val loaded = reader.load(eager = false, preferredId = "w-45")!!
        assertEquals("w-45", loaded.activeWorkId)
        assertEquals(99, reader.deferredWorkIds.size)
        assertEquals(1, docs.reads.count { work -> work.endsWith(".json") && !work.startsWith("index.") })
        val deferred = loaded.works.first()
        assertFalse(deferred.bodyLoaded)
        assertEquals(setOf("helper.js", "shader.frag"), deferred.files.keys)
        assertEquals("9:16", deferred.previewAspectRatio)
        assertEquals("0.20.0", deferred.libraries["matter-js"])
        assertEquals("source-0", reader.loadWork("w-0")!!.code)
        assertEquals(98, reader.deferredWorkIds.size)
    }

    @Test fun deferredMetadataSaveAndRepeatedMaterializationNeverReplaceBodyWithPlaceholders() {
        val docs = Documents(); val original = works()
        original.last().files["helper.js"] = "B helper"
        assertTrue(SplitWorkStore(docs).save(original, "a"))
        val reader = SplitWorkStore(docs)
        val lazy = reader.load(eager = false)!!.works
        val renamed = lazy.map(::snapshotWork).also { it.last().title = "Renamed B"; it.last().isPinned = true }
        assertFalse(renamed.last().bodyLoaded)
        repeat(2) {
            val full = reader.materializeWorks(renamed).last()
            assertEquals("second", full.code)
            assertEquals("B helper", full.files["helper.js"])
            assertEquals("Renamed B", full.title)
        }
        assertTrue(reader.save(renamed, "a"))
        val saved = SplitWorkStore(docs).load()!!.works.last()
        assertEquals("second", saved.code)
        assertEquals("B helper", saved.files["helper.js"])
        assertEquals("Renamed B", saved.title)
        assertTrue(saved.isPinned)
    }

    @Test fun cachedHydrationStillProtectsAnOlderUiPlaceholderAfterAssetRestoreFailure() {
        val docs = Documents(); val original = works()
        assertTrue(SplitWorkStore(docs).save(original, "a"))
        val reader = SplitWorkStore(docs)
        val placeholders = reader.load(eager = false)!!.works
        // Repository asset preparation can fail after loadWork has cached the body.
        assertEquals("second", reader.loadWork("b")!!.code)
        assertTrue(reader.save(placeholders, "a"))
        assertEquals("second", SplitWorkStore(docs).load()!!.works.last().code)
    }

    @Test fun eagerReadStillFallsBackWhenAnInactiveDocumentIsDamaged() {
        val docs = Documents(); val writer = SplitWorkStore(docs); val original = works()
        assertTrue(writer.save(original, "a"))
        assertTrue(writer.save(original.map(::snapshotWork).also { it.last().code = "new B" }, "a"))
        val inactive = paths(docs.files.getValue("index.json")).last()
        docs.files[inactive] = "corrupt B"
        val lazy = SplitWorkStore(docs)
        assertEquals("first", lazy.load(eager = false)!!.works.first().code)
        assertTrue(runCatching { lazy.loadWork("b") }.isFailure)
        val eager = SplitWorkStore(docs)
        assertEquals("second", eager.load()!!.works.last().code)
        assertTrue(eager.recovered)
    }

    @Test fun firstLazySaveVerifiesDeferredBodiesOnceAndLaterSavesUseTheCache() {
        val docs = Documents(); val original = works()
        assertTrue(SplitWorkStore(docs).save(original, "a"))
        val reader = SplitWorkStore(docs)
        val lazy = reader.load(eager = false)!!.works
        val edited = lazy.map(::snapshotWork).also { it.first().code = "first update" }
        docs.resetCounts()
        assertEquals(WorkSaveResult.Saved, reader.saveResult(edited, "a"))
        val unchangedBody = paths(docs.files.getValue("index.json")).last()
        assertEquals(1, docs.reads.count { it == unchangedBody })
        val again = edited.map(::snapshotWork).also { it.first().code = "second update" }
        docs.resetCounts()
        assertEquals(WorkSaveResult.Saved, reader.saveResult(again, "a"))
        assertFalse(unchangedBody in docs.reads)
        assertEquals("second", SplitWorkStore(docs).load()!!.works.last().code)
    }

    @Test fun inactiveBodyDamageBlocksLazySavesAndPreservesTheCompleteBackupAcrossRetries() {
        val docs = Documents(); val writer = SplitWorkStore(docs); val original = works()
        assertTrue(writer.save(original, "a"))
        val current = original.map(::snapshotWork).also { it.last().code = "changed B" }
        assertTrue(writer.save(current, "a"))
        val inactive = paths(docs.files.getValue("index.json")).last()
        docs.files[inactive] = "damaged"
        val before = docs.files.toMap()
        val reader = SplitWorkStore(docs)
        val lazy = reader.load(eager = false)!!.works.map(::snapshotWork).also { it.first().code = "local A edit" }
        repeat(2) { assertEquals(WorkSaveResult.Failed, reader.saveResult(lazy, "a")) }
        assertEquals(before, docs.files)
        val recovery = SplitWorkStore(docs)
        assertEquals(listOf("first", "second"), recovery.load()!!.works.map { it.code })
        assertTrue(recovery.recovered)
    }

    @Test fun conflictCleanupRetainsACreatedBodyNowReferencedByAnotherWritersIndex() {
        val docs = Documents(); val writer = SplitWorkStore(docs); val original = works()
        assertTrue(writer.save(original, "a"))
        var sharedBody: String? = null
        docs.afterWrite = { name, _ ->
            if (!name.startsWith("index.")) sharedBody = name
            if (name == "index.json.pending") {
                // Another writer reuses the exact immutable body before publishing its index.
                val index = JSONObject(docs.files.getValue("index.json"))
                index.getJSONArray("works").getJSONObject(0).put("file", sharedBody!!)
                docs.files["index.json"] = index.toString()
            }
        }
        val edited = original.map(::snapshotWork).also { it.first().code = "same shared update" }
        assertEquals(WorkSaveResult.Conflict, writer.saveResult(edited, "a"))
        assertNotNull(sharedBody)
        assertTrue(docs.files.containsKey(sharedBody))
        assertEquals("same shared update", SplitWorkStore(docs).load()!!.works.first().code)
    }

    @Test fun conflictCleanupKeepsCreatedBodiesWhenPublishedIndexCannotBeDecodedSafely() {
        val docs = Documents(); val writer = SplitWorkStore(docs); val original = works()
        assertTrue(writer.save(original, "a"))
        var created: String? = null
        docs.afterWrite = { name, _ ->
            if (!name.startsWith("index.")) created = name
            if (name == "index.json.pending") docs.files["index.json"] = "unreadable external index"
        }
        assertEquals(WorkSaveResult.Conflict, writer.saveResult(original.map(::snapshotWork).also { it.first().code = "new" }, "a"))
        assertTrue(docs.files.containsKey(created))
    }

}
