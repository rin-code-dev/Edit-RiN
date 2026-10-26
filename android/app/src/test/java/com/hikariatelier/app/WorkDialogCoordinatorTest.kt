package com.hikariatelier.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkDialogCoordinatorTest {

    @Test
    fun backStackNavigationFollowsHierarchy() {
        val coordinator = WorkDialogCoordinator()

        // Open WorkSettings root
        coordinator.openRoot(WorkDialogDestination.WorkSettings(workId = "work1", generation = 1L))
        assertTrue(coordinator.isCurrent<WorkDialogDestination.WorkSettings>())

        // Push AssetManager child
        coordinator.push(WorkDialogDestination.AssetManager(workId = "work1", generation = 1L))
        assertTrue(coordinator.isCurrent<WorkDialogDestination.AssetManager>())

        // Push AssetPreview child
        coordinator.push(WorkDialogDestination.AssetPreview(workId = "work1", generation = 1L, assetName = "hero.png"))
        assertTrue(coordinator.isCurrent<WorkDialogDestination.AssetPreview>())
        assertEquals("hero.png", coordinator.currentAs<WorkDialogDestination.AssetPreview>()?.assetName)

        // Pop back to AssetManager
        assertTrue(coordinator.pop())
        assertTrue(coordinator.isCurrent<WorkDialogDestination.AssetManager>())

        // Pop back to WorkSettings
        assertTrue(coordinator.pop())
        assertTrue(coordinator.isCurrent<WorkDialogDestination.WorkSettings>())

        // Pop dismisses root
        assertTrue(coordinator.pop())
        assertFalse(coordinator.isShowing)
        assertNull(coordinator.currentDestination)
    }

    @Test
    fun directEntryDoesNotInsertIntermediateScreens() {
        val coordinator = WorkDialogCoordinator()

        // Directly open AssetManager from editor
        coordinator.openRoot(WorkDialogDestination.AssetManager(workId = "work1", generation = 1L))
        assertTrue(coordinator.isCurrent<WorkDialogDestination.AssetManager>())

        // Back returns directly to editor (empty stack)
        assertTrue(coordinator.pop())
        assertFalse(coordinator.isShowing)
    }

    @Test
    fun workSwitchInvalidatesPendingDialogs() {
        val coordinator = WorkDialogCoordinator()

        coordinator.openRoot(WorkDialogDestination.WorkSettings(workId = "work1", generation = 1L))
        coordinator.push(WorkDialogDestination.RuntimeSettings(workId = "work1", generation = 1L))
        assertTrue(coordinator.isShowing)

        // Switch to work2
        coordinator.invalidateForWorkChange(currentWorkId = "work2", currentGeneration = 1L)
        assertFalse(coordinator.isShowing)
        assertNull(coordinator.currentDestination)
    }

    @Test
    fun generationChangeInvalidatesPendingDialogs() {
        val coordinator = WorkDialogCoordinator()

        coordinator.openRoot(WorkDialogDestination.ProjectFiles(workId = "work1", generation = 1L))
        assertTrue(coordinator.isShowing)

        // Generation advanced
        coordinator.invalidateForWorkChange(currentWorkId = "work1", currentGeneration = 2L)
        assertFalse(coordinator.isShowing)
    }
}
