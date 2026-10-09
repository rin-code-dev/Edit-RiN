package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class GalleryMotionTest {
    @Test fun toolbarArrivesAfterTheEditorAndPrimaryButtonsAreStaggered() {
        val half = 0.5f
        assertTrue(galleryExposure(half, GalleryPart.EDITOR) > galleryExposure(half, GalleryPart.TITLE))
        assertTrue(galleryExposure(half, GalleryPart.TITLE) > galleryExposure(half, GalleryPart.PRIMARY))
        assertTrue(galleryExposure(half, GalleryPart.PRIMARY, 0) > galleryExposure(half, GalleryPart.PRIMARY, 3))
        assertTrue(galleryExposure(half, GalleryPart.PRIMARY) > galleryExposure(half, GalleryPart.CONTROLS))
        // The gallery has withdrawn before the editor finishes, exposing the reveal itself.
        assertEquals(0f, galleryLayerAlpha(0.45f), 0f)
        assertTrue(galleryExposure(0.45f, GalleryPart.EDITOR) in 0.1f..0.9f)
    }
    @Test fun interruptedOrReversedMotionUsesTheSameStateWithoutQueuedDelays() {
        for (part in GalleryPart.entries) {
            val forward = (0..100).map { galleryExposure(it / 100f, part) }
            val reverse = (100 downTo 0).map { galleryExposure(it / 100f, part) }
            assertEquals(forward, reverse.reversed())
            assertEquals(1f, forward.first(), 0f)
            assertEquals(0f, forward.last(), 0f)
            assertTrue(forward.zipWithNext().all { (before, after) -> before >= after })
            assertEquals(1f, galleryExposure(Float.NaN, part), 0f)
        }
    }
}
