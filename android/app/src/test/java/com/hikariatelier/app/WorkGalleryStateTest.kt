package com.hikariatelier.app

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.*
import org.junit.Test

class WorkGalleryStateTest {
    private fun gallery() = WorkGalleryState(
        mutableStateOf(false), mutableStateOf(""), mutableStateOf(null),
        mutableStateOf(LazyGridState(5, 40)), mutableStateOf(false), mutableStateOf(emptySet())
    )

    @Test fun switchingCollectionReplacesGridAndClearsOldActions() {
        val gallery = gallery()
        val previousGrid = gallery.gridState
        gallery.startSelection("work")
        gallery.selectedTag = "tag"
        gallery.cardMenuWorkId = "work"
        gallery.jumpToWork("work")
        gallery.changeFolder(SAMPLE_FOLDER)
        assertNotSame(previousGrid, gallery.gridState)
        assertEquals(0, gallery.gridState.firstVisibleItemIndex)
        assertEquals(0, gallery.gridState.firstVisibleItemScrollOffset)
        assertNull(gallery.jumpWorkId)
        assertNull(gallery.selectedTag)
        assertNull(gallery.cardMenuWorkId)
        assertFalse(gallery.selecting)
        assertTrue(gallery.selectedIds.isEmpty())
        val sampleGrid = gallery.gridState
        gallery.changeFolder(null)
        assertNotSame(sampleGrid, gallery.gridState)
        gallery.changeFolder("")
        assertNotSame(sampleGrid, gallery.gridState)
    }

    @Test fun choosingSameCollectionKeepsScrollState() {
        val gallery = gallery()
        val grid = gallery.gridState
        gallery.changeFolder(null)
        assertSame(grid, gallery.gridState)
        assertEquals(5, gallery.gridState.firstVisibleItemIndex)
        assertEquals(40, gallery.gridState.firstVisibleItemScrollOffset)
    }

    @Test fun searchAllFoldersExpandsScopeOnlyWhileSearchingWithQueryAndResetsOnClose() {
        val gallery = gallery()
        val inFolderA = Work("a", "A", "", folderName = "FolderA")
        val inFolderB = Work("b", "B", "", folderName = "FolderB")
        val sample = Work("s", "Sample", "", isSample = true)

        gallery.changeFolder("FolderA")
        assertTrue(gallery.matchesScope(inFolderA))
        assertFalse(gallery.matchesScope(inFolderB))
        assertFalse(gallery.matchesScope(sample))

        gallery.searching = true
        gallery.query = "noise"
        gallery.searchAllFolders = true
        assertTrue(gallery.matchesScope(inFolderA))
        assertTrue(gallery.matchesScope(inFolderB))
        assertTrue(gallery.matchesScope(sample))

        gallery.closeSearch()
        assertFalse(gallery.searchAllFolders)
        assertFalse(gallery.matchesScope(inFolderB))
    }
}
