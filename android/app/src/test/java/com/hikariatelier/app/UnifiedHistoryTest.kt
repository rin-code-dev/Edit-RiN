package com.hikariatelier.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedHistoryTest {

    @Test
    fun buildHistoryDisplayItemsCombinesAndSortsCorrectly() {
        val revisions = listOf(
            WorkRevision(savedAt = 1000L, code = "line 1"),
            WorkRevision(savedAt = 3000L, code = "line 3")
        )
        val snapshots = listOf(
            WorkSnapshot(
                id = "snap1",
                savedAt = 2000L,
                title = "Step 1",
                code = "line 2",
                files = emptyMap(),
                parameterValues = emptyMap(),
                blockEditorState = null
            ),
            WorkSnapshot(
                id = "snap2",
                savedAt = 4000L,
                title = "Step 2",
                code = "line 4",
                files = mapOf("helper.js" to "let x = 1;"),
                parameterValues = mapOf("speed" to "5"),
                blockEditorState = "{}"
            )
        )

        val items = buildHistoryDisplayItems(
            workId = "work1",
            revisions = revisions,
            snapshots = snapshots,
            uiText = { it }
        )

        // Count
        assertEquals(4, items.size)

        // Sorted descending by timestamp
        assertEquals(4000L, items[0].timestamp)
        assertEquals(3000L, items[1].timestamp)
        assertEquals(2000L, items[2].timestamp)
        assertEquals(1000L, items[3].timestamp)

        // Types and scopes
        assertEquals(HistoryItemType.SNAPSHOT, items[0].type)
        assertTrue(items[0].restoreScopeSummary.contains("追加ファイル"))
        assertTrue(items[0].restoreScopeSummary.contains("パラメータ"))
        assertTrue(items[0].restoreScopeSummary.contains("ブロック配置"))

        assertEquals(HistoryItemType.REVISION, items[1].type)
        assertEquals("sketch.js 本文のみ復元", items[1].restoreScopeSummary)

        assertEquals(HistoryItemType.LEGACY_SNAPSHOT, items[2].type)
        assertEquals("コードのみ復元（旧形式）", items[2].restoreScopeSummary)

        assertEquals(HistoryItemType.REVISION, items[3].type)
    }

    @Test
    fun distinctIdentitiesForSameTimestampRecords() {
        val rev = WorkRevision(savedAt = 5000L, code = "same code")
        val snap = WorkSnapshot(
            id = "snap_distinct",
            savedAt = 5000L,
            title = "Manual Save",
            code = "same code"
        )

        val items = buildHistoryDisplayItems(
            workId = "work1",
            revisions = listOf(rev),
            snapshots = listOf(snap),
            uiText = { it }
        )

        assertEquals(2, items.size)
        // IDs are distinct
        assertTrue(items[0].id != items[1].id)
    }
}
