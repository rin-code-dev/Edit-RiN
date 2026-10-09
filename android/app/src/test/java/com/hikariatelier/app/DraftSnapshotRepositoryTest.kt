package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DraftSnapshotRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun authoredBaseHashIsStableAcrossFileOrderAndMetadataOnlyChanges() {
        val first = Work("a", "A", "main", files = linkedMapOf("b.js" to "b", "a.js" to "a"))
        val second = Work("a", "Renamed", "main", files = linkedMapOf("a.js" to "a", "b.js" to "b"), isPinned = true)
        assertEquals(draftBaseHash(first), draftBaseHash(second))
        second.files["a.js"] = "changed"
        assertNotEquals(draftBaseHash(first), draftBaseHash(second))
        assertNotEquals(draftBaseHash(Work("a", "A", "ab", files = mutableMapOf("c" to "d"))),
            draftBaseHash(Work("a", "A", "a", files = mutableMapOf("bc" to "d"))))
    }

    @Test fun matchingScopedDraftRestoresCodeAndOnlyUnchangedExistingAuxiliaryBases() {
        val file = File(temporary.newFolder(), "draft.json")
        val repo = DraftSnapshotRepository(file)
        try {
            val first = Work("a", "A", "main", files = mutableMapOf("helper.js" to "helper"))
            val second = Work("b", "B", "other", files = mutableMapOf("other.js" to "old"))
            repo.save("a", "unsaved", mapOf("a/helper.js" to "unsaved helper", "b/other.js" to "other draft", "missing/no.js" to "unknown"),
                "folder-a", draftBaseHash(first), mapOf("a" to draftBaseHash(first), "b" to draftBaseHash(second)))
            repo.awaitPendingWrites()
            second.files["other.js"] = "externally changed"
            val compatible = repo.loadCompatible("folder-a", listOf(first, second))!!
            assertEquals("unsaved", compatible.first.code)
            assertEquals(mapOf("a/helper.js" to "unsaved helper"), compatible.second)
            assertEquals(3, repo.load()!!.second.size) // Filter does not destroy explicit-recovery data.
        } finally { repo.close() }
    }

    @Test fun sameWorkIdInAnotherFolderAndChangedBaseNeverAutoRestoreOrDeleteDraft() {
        val file = File(temporary.newFolder(), "draft.json")
        val repo = DraftSnapshotRepository(file)
        try {
            val work = Work("gravity", "Gravity", "saved")
            repo.save(work.id, "folder-A edit", emptyMap(), "folder-A", draftBaseHash(work))
            repo.awaitPendingWrites()
            assertNull(repo.loadCompatible("local", listOf(work)))
            assertNull(repo.loadCompatible("folder-B", listOf(work)))
            work.code = "newer saved code"
            assertNull(repo.loadCompatible("folder-A", listOf(work)))
            assertEquals("folder-A edit", repo.load()!!.first.code)
            assertTrue(file.exists())
        } finally { repo.close() }
    }

    @Test fun unscopedLegacyDraftStaysAvailableForExplicitRecovery() {
        val file = File(temporary.newFolder(), "draft.json")
        file.writeText("""{"workId":"a","code":"legacy edit","updatedAt":1,"fileDrafts":{}}""")
        val repo = DraftSnapshotRepository(file)
        try {
            assertNull(repo.loadCompatible("local", listOf(Work("a", "A", "saved"))))
            assertEquals("legacy edit", repo.load()!!.first.code)
            repo.clear(); repo.awaitPendingWrites()
            assertNull(repo.load())
        } finally { repo.close() }
    }

    @Test fun latestQueuedSaveAndClearLeaveOnlyTheNewestDraft() {
        val repo = DraftSnapshotRepository(File(temporary.newFolder(), "draft.json"))
        try {
            val work = Work("a", "A", "saved")
            repeat(50) { repo.save("a", "draft-$it", emptyMap(), "local", draftBaseHash(work)) }
            repo.clear()
            repo.save("a", "latest", emptyMap(), "local", draftBaseHash(work))
            repo.awaitPendingWrites()
            assertEquals("latest", repo.loadCompatible("local", listOf(work))!!.first.code)
        } finally { repo.close() }
    }
    @Test fun delayedHashResultCannotReplaceNewerSaveOrResurrectClearedDraft() {
        val repo = DraftSnapshotRepository(File(temporary.newFolder(), "draft.json"))
        try {
            val old = repo.beginGeneration()
            val newer = repo.beginGeneration()
            repo.save("a", "latest", emptyMap(), generationToken = newer)
            repo.save("a", "obsolete", emptyMap(), generationToken = old)
            repo.awaitPendingWrites()
            assertEquals("latest", repo.load()!!.first.code)
            val computing = repo.beginGeneration()
            repo.clear()
            repo.save("a", "late hash", emptyMap(), generationToken = computing)
            repo.awaitPendingWrites()
            assertNull(repo.load())
        } finally { repo.close() }
    }

    @Test fun clearingWhileHashCalculationRunsRejectsItsResult() {
        val repo = DraftSnapshotRepository(File(temporary.newFolder(), "draft.json"))
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        try {
            val token = repo.beginGeneration()
            repo.compute(token) {
                entered.countDown()
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                repo.save("a", "obsolete result", emptyMap(), generationToken = token)
            }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            repo.clear()
            release.countDown()
            repo.awaitPendingWrites()
            assertNull(repo.load())
        } finally { release.countDown(); repo.close() }
    }

}
