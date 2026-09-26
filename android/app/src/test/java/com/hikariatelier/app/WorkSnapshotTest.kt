package com.hikariatelier.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkSnapshotTest {
    @get:Rule val temp = TemporaryFolder()
    private fun content(code: String = "main") = SnapshotContent(code, mapOf("shader.frag" to "shader"), mapOf("speed" to "2"))

    @Test fun capturesUnsavedSupportingFilesWithoutSharingDraftMaps() {
        val work = Work("a", "A", "old", files = mutableMapOf("helper.js" to "old helper", "shader.frag" to "old shader"))
        val drafts = mutableMapOf("a/helper.js" to "new helper", "a/shader.frag" to "new shader", "b/helper.js" to "other")
        val captured = currentSnapshotContent(work, "new main", drafts)
        drafts["a/helper.js"] = "later typing"
        assertEquals("new main", captured.code)
        assertEquals(mapOf("helper.js" to "new helper", "shader.frag" to "new shader"), captured.files)
        assertEquals("old helper", work.files["helper.js"])
    }

    @Test fun matchingAndDiffIncludeFilesParametersAndEmptyFileDeletion() {
        val current = content()
        val snapshot = WorkSnapshot(code = current.code, files = current.files, parameterValues = current.parameterValues)
        assertTrue(snapshot.matches(current))
        assertFalse(snapshot.copy(files = mapOf("shader.frag" to "changed")).matches(current))
        assertFalse(snapshot.copy(parameterValues = mapOf("speed" to "3")).matches(current))
        val target = snapshot.copy(files = mapOf("empty.js" to ""), parameterValues = mapOf("speed" to "3"))
        val diff = snapshotDifference(current, target)
        assertTrue(diff.contains("shader.frag"))
        assertTrue(diff.contains("+ empty.js"))
        assertTrue(diff.contains("rinParams.speed"))
        assertTrue(diff.contains("+ 3"))
    }

    @Test fun preparingRestoreDoesNotMutateSessionAndLegacyKeepsSupportingFiles() {
        val work = Work("a", "A", "saved", files = mutableMapOf("helper.js" to "saved helper"))
        val current = content("unsaved main")
        val restored = restoredSnapshotWork(work, WorkSnapshot(code = "historic", mainCodeOnly = true), current)
        assertEquals("saved", work.code)
        assertEquals(mapOf("helper.js" to "saved helper"), work.files.toMap())
        assertEquals("historic", restored.code)
        assertEquals(current.files, restored.files.toMap())
        assertEquals(current.parameterValues, restored.parameterValues.toMap())
    }

    @Test fun failedPersistenceNeverCommitsEditorState() = runBlocking {
        var commits = 0
        assertFalse(persistSnapshotRestore({ false }) { commits++ })
        assertEquals(0, commits)
        assertTrue(runCatching { persistSnapshotRestore({ error("full disk") }) { commits++ } }.isFailure)
        assertEquals(0, commits)
        assertTrue(persistSnapshotRestore({ true }) { commits++ })
        assertEquals(1, commits)
    }

    @Test fun slowPersistenceDoesNotBlockCallerEventLoopAndCommitsOnCaller() {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { ui ->
            runBlocking(ui) {
                val caller = Thread.currentThread()
                val entered = CompletableDeferred<Unit>()
                val release = CountDownLatch(1)
                var committed = false
                val operation = async {
                    persistSnapshotRestore({
                        assertNotSame(caller, Thread.currentThread())
                        entered.complete(Unit)
                        check(release.await(5, TimeUnit.SECONDS))
                        true
                    }) {
                        assertSame(caller, Thread.currentThread())
                        committed = true
                    }
                }
                try {
                    withTimeout(3000) {
                        entered.await()
                        assertEquals(42, async { 42 }.await())
                        assertFalse(committed)
                    }
                } finally { release.countDown() }
                assertTrue(operation.await())
                assertTrue(committed)
            }
        }
    }

    @Test fun snapshotRoundTripRetainsLatestFifteenAndExplicitEmptyHistory() {
        val dir = temp.newFolder()
        repeat(18) { WorkSnapshotStore.addSnapshot(dir, "a", content("main-$it")) }
        var saved = WorkSnapshotStore.loadSnapshots(dir, "a")
        assertEquals(15, saved.size)
        assertEquals("main-17", saved.first().code)
        assertEquals(content().files, saved.first().files)
        saved.toList().forEach { WorkSnapshotStore.deleteSnapshot(dir, "a", it.id) }
        saved = WorkSnapshotStore.loadSnapshots(dir, "a", listOf(WorkRevision("legacy", 1)))
        assertTrue(saved.isEmpty())
    }

    @Test fun corruptHistoryIsReportedAndNeverOverwrittenByAddOrDelete() {
        val dir = temp.newFolder()
        val file = File(dir, "snapshots/a.json").apply { parentFile!!.mkdirs(); writeText("{broken") }
        assertTrue(runCatching { WorkSnapshotStore.loadSnapshots(dir, "a") }.isFailure)
        assertTrue(runCatching { WorkSnapshotStore.addSnapshot(dir, "a", content()) }.isFailure)
        assertTrue(runCatching { WorkSnapshotStore.deleteSnapshot(dir, "a", "x") }.isFailure)
        assertEquals("{broken", file.readText())
    }

    @Test fun failedPendingWriteRetainsPreviousFileAndReportsFailure() {
        val dir = temp.newFolder()
        WorkSnapshotStore.addSnapshot(dir, "a", content("old"))
        val file = File(dir, "snapshots/a.json")
        val old = file.readBytes()
        File(file.path + ".new").mkdir()
        assertTrue(runCatching { WorkSnapshotStore.addSnapshot(dir, "a", content("new")) }.isFailure)
        assertArrayEquals(old, file.readBytes())
        assertEquals("old", WorkSnapshotStore.loadSnapshots(dir, "a").single().code)
    }

    @Test fun interruptedCommitRecoversBackupAndIgnoresUncommittedPendingFile() {
        val file = File(temp.newFolder(), "state.json")
        file.writeText("incomplete new")
        File(file.path + ".bak").writeText("old")
        File(file.path + ".new").writeText("uncommitted")
        assertEquals("old", SnapshotAtomicFile(file).read())
        SnapshotAtomicFile(file).write("complete")
        assertEquals("complete", SnapshotAtomicFile(file).read())
        assertFalse(File(file.path + ".bak").exists())
    }

    @Test fun oldMigrationRemainsMainCodeOnlyAndLoadingDoesNotWrite() {
        val dir = temp.newFolder()
        val migrated = WorkSnapshotStore.loadSnapshots(dir, "a", listOf(WorkRevision("old", 1)))
        assertTrue(migrated.single().mainCodeOnly)
        assertFalse(File(dir, "snapshots").exists())
        val oldJson = JSONArray().put(JSONObject().put("id", "migrated_0").put("code", "old")
            .put("files", JSONObject()).put("parameterValues", JSONObject()))
        assertTrue(decodeSnapshots(oldJson).single().mainCodeOnly)
    }

    @Test fun backgroundLegacyMigrationIsIdempotentAndPreservesEmptyHistory() {
        val dir = temp.newFolder()
        val revisions = (1..30).map { WorkRevision("old-$it", it.toLong()) }
        WorkSnapshotStore.ensureLegacyMigrated(dir, "a", revisions)
        val migrated = WorkSnapshotStore.loadSnapshots(dir, "a")
        assertEquals(10, migrated.size)
        assertEquals("old-30", migrated.first().code)
        WorkSnapshotStore.ensureLegacyMigrated(dir, "a", listOf(WorkRevision("later", 40)))
        assertEquals(migrated, WorkSnapshotStore.loadSnapshots(dir, "a"))
        WorkSnapshotStore.saveSnapshots(dir, "a", emptyList())
        WorkSnapshotStore.ensureLegacyMigrated(dir, "a", revisions)
        assertTrue(WorkSnapshotStore.loadSnapshots(dir, "a").isEmpty())
    }

    @Test fun safeExistingImportedIdsKeepTheirOriginalHistoryFilename() {
        val dir = temp.newFolder()
        val id = "作品 one.v1"
        val file = File(dir, "snapshots/$id.json").apply { parentFile!!.mkdirs() }
        val previous = listOf(WorkSnapshot(code = "before"))
        file.writeText(encodeSnapshots(previous).toString())
        assertEquals(previous, WorkSnapshotStore.loadSnapshots(dir, id))
        WorkSnapshotStore.addSnapshot(dir, id, content("after"))
        assertEquals("after", WorkSnapshotStore.loadSnapshots(dir, id).first().code)
    }

    @Test fun importedUnsafeWorkIdCannotEscapeSnapshotDirectory() {
        val dir = temp.newFolder()
        WorkSnapshotStore.addSnapshot(dir, "../outside", content())
        assertFalse(File(dir, "outside.json").exists())
        assertEquals(1, File(dir, "snapshots").listFiles()!!.size)
        assertEquals("main", WorkSnapshotStore.loadSnapshots(dir, "../outside").single().code)
    }

    @Test fun backupRoundTripCarriesHistoryAndWorkImportCanRemapId() {
        val files = temp.newFolder()
        val source = AssetStorage(temp.newFolder())
        val work = Work("a", "A", "main")
        val original = WorkSnapshotStore.addSnapshot(files, "a", content())
        val bytes = ByteArrayOutputStream()
        writeAssetBackup(bytes, listOf(work), work.id, "{}", source, WorkSnapshotStore.exportSnapshots(files, listOf(work)))
        val backup = readAssetBackup(ByteArrayInputStream(bytes.toByteArray()), AssetStorage(temp.newFolder()))
        assertEquals(original, backup.snapshots["a"])
        val target = temp.newFolder()
        assertTrue(WorkSnapshotStore.importWithWorks(target, mapOf("new-id" to backup.snapshots.getValue("a"))) { true })
        assertEquals(original, WorkSnapshotStore.loadSnapshots(target, "new-id"))
    }

    @Test fun importRollsBackOldAndNewHistoriesWhenWorkSaveFails() {
        val dir = temp.newFolder()
        val old = WorkSnapshotStore.addSnapshot(dir, "a", content("old"))
        val incoming = listOf(WorkSnapshot(code = "new"))
        assertTrue(runCatching {
            WorkSnapshotStore.importWithWorks(dir, mapOf("a" to incoming, "b" to incoming)) { false }
        }.isFailure)
        assertEquals(old, WorkSnapshotStore.loadSnapshots(dir, "a"))
        assertTrue(WorkSnapshotStore.loadSnapshots(dir, "b").isEmpty())
    }

    @Test fun malformedBackupHistoryIsRejectedBeforePublishingAssets() {
        val source = AssetStorage(temp.newFolder())
        val asset = source.put(ByteArrayInputStream(byteArrayOf(1, 2)), "text/plain")
        val work = Work("a", "A", "", assets = mapOf("a.txt" to asset))
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            fun entry(name: String, value: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(value); zip.closeEntry()
            }
            entry("works.json", serializeWorkStore(listOf(work), "a").toByteArray())
            entry("assets/${asset.hash}", byteArrayOf(1, 2))
            entry("snapshots.json", """{"version":1,"works":{"unknown":[]}}""".toByteArray())
        }
        val target = AssetStorage(temp.newFolder())
        assertTrue(runCatching { readAssetBackup(ByteArrayInputStream(bytes.toByteArray()), target) }.isFailure)
        assertFalse(target.contains(asset))
    }

    @Test fun largeHistoryLocalTimingProbe() {
        val dir = temp.newFolder()
        val code = "const sample = 123;\n".repeat(5000)
        val history = (1..15).map { WorkSnapshot(savedAt = it.toLong(), code = code, files = mapOf("helper.js" to code)) }
        val start = System.nanoTime()
        WorkSnapshotStore.saveSnapshots(dir, "large", history)
        val saved = System.nanoTime()
        val restored = WorkSnapshotStore.loadSnapshots(dir, "large")
        val loaded = System.nanoTime()
        assertEquals(15, restored.size)
        assertEquals(code, restored.first().files["helper.js"])
        println("SNAPSHOT_TIMING bytes=${File(dir, "snapshots/large.json").length()} saveMs=${(saved-start)/1_000_000} loadMs=${(loaded-saved)/1_000_000}")
    }
}
