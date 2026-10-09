package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class ThumbnailPolicyTest {
    private val fast = ThumbnailDeviceState(8, 512, 2048, false, false)
    @Test fun budgetUsesMemoryPowerAndTemperatureNotOnlyCoreCount() {
        assertEquals(2, thumbnailWorkerCount(fast))
        assertEquals(1, thumbnailWorkerCount(fast.copy(lowRam = true)))
        assertEquals(1, thumbnailWorkerCount(fast.copy(heapMb = 128)))
        assertEquals(1, thumbnailWorkerCount(fast.copy(availableMb = 256)))
        assertEquals(1, thumbnailWorkerCount(fast.copy(cores = 2)))
        assertEquals(1, thumbnailWorkerCount(fast.copy(powerSave = true)))
        assertEquals(1, thumbnailWorkerCount(fast.copy(thermalStatus = 1)))
        assertEquals(0, thumbnailWorkerCount(fast.copy(thermalStatus = 3)))
        assertEquals(0, thumbnailWorkerCount(fast.copy(lowMemory = true)))
        assertEquals(0, thumbnailWorkerCount(fast.copy(availableMb = 64)))
        assertEquals(0, thumbnailWorkerCount(fast.copy(heapAvailableMb = 16)))
        assertEquals(1, thumbnailWorkerCount(fast.copy(heapAvailableMb = 48)))
    }
    @Test fun visiblePriorityCanChangeWithoutRestartingRemainingQueue() {
        val remaining = linkedSetOf("old", "active", "visible", "next")
        assertEquals("visible", nextThumbnailId(remaining, listOf("deleted", "visible", "next"), "active"))
        remaining.remove("visible")
        assertEquals("next", nextThumbnailId(remaining, listOf("next"), "active"))
        assertEquals("active", nextThumbnailId(remaining, emptyList(), "active"))
        assertEquals("old", nextThumbnailId(remaining, emptyList(), "deleted"))
        assertNull(nextThumbnailId(emptySet(), listOf("visible"), "active"))
    }
    @Test fun uiLoadThrottlesWorkerCountWithinDeviceLimits() {
        // Fast device: normal=2, jankActive=1, heavyLoad=0
        assertEquals(2, thumbnailWorkerCount(fast, ThumbnailLoadState()))
        assertEquals(1, thumbnailWorkerCount(fast, ThumbnailLoadState(jankActive = true)))
        assertEquals(0, thumbnailWorkerCount(fast, ThumbnailLoadState(heavyLoad = true)))

        // 1-worker device: normal=1, jankActive=0, heavyLoad=0
        val mid = fast.copy(lowRam = true)
        assertEquals(1, thumbnailWorkerCount(mid, ThumbnailLoadState()))
        assertEquals(0, thumbnailWorkerCount(mid, ThumbnailLoadState(jankActive = true)))
        assertEquals(0, thumbnailWorkerCount(mid, ThumbnailLoadState(heavyLoad = true)))
    }
}
