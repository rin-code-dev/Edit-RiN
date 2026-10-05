package com.hikariatelier.app

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryTransitionTest {
    private val preview = Rect(10f, 20f, 310f, 220f)
    private val thumbnail = Rect(40f, 500f, 160f, 620f)

    @Test fun endpointsAndMidpointInterpolatePositionAndSizeTogether() {
        assertEquals(preview, interpolateGalleryBounds(preview, thumbnail, 0f))
        assertEquals(thumbnail, interpolateGalleryBounds(preview, thumbnail, 1f))
        assertEquals(Rect(25f, 260f, 235f, 420f), interpolateGalleryBounds(preview, thumbnail, 0.5f))
    }

    @Test fun outOfRangeProgressIsClampedAndNonFiniteProgressKeepsTheStart() {
        assertEquals(preview, interpolateGalleryBounds(preview, thumbnail, -1f))
        assertEquals(thumbnail, interpolateGalleryBounds(preview, thumbnail, 2f))
        for (progress in listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)) {
            assertEquals(preview, interpolateGalleryBounds(preview, thumbnail, progress))
        }
    }

    @Test fun unmeasuredOrInvalidBoundsUseTheValidEndpointOrAnEmptySentinel() {
        val invalid = listOf(Rect.Zero, Rect(10f, 20f, 9f, 21f), Rect(Float.NaN, 0f, 20f, 30f),
            Rect(0f, 0f, Float.POSITIVE_INFINITY, 30f), Rect(-Float.MAX_VALUE, 0f, Float.MAX_VALUE, 30f))
        for (bounds in invalid) {
            assertEquals(thumbnail, interpolateGalleryBounds(bounds, thumbnail, 0.4f))
            assertEquals(preview, interpolateGalleryBounds(preview, bounds, 0.4f))
            assertEquals(Rect.Zero, interpolateGalleryBounds(bounds, Rect.Zero, 0.4f))
        }
    }

    @Test fun partiallyOffscreenBoundsAndReverseMotionRemainValid() {
        val offscreen = Rect(-80f, -40f, 120f, 160f)
        assertEquals(Rect(-20f, 230f, 140f, 390f), interpolateGalleryBounds(offscreen, thumbnail, 0.5f))
        assertEquals(interpolateGalleryBounds(offscreen, thumbnail, 0.75f),
            interpolateGalleryBounds(thumbnail, offscreen, 0.25f))
    }
}
