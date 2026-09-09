package com.hikariatelier.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewGeometryTest {
    @Test fun hiddenPreviewReleasesAllSpaceAndRestoresOriginalGeometry() {
        for (compact in listOf(true, false)) {
            assertEquals(0f, portraitPreviewHeight(360f, 240f, 9f / 16f, true, compact), 0f)
        }
        assertEquals(144f, portraitPreviewHeight(360f, 240f, 9f / 16f, false, true), 0.001f)
        assertEquals(240f, portraitPreviewHeight(360f, 240f, 9f / 16f, false, false), 0.001f)
    }

    @Test
    fun deviceRatioIsAcceptedAndResolvedAtRuntime() {
        assertEquals("device", normalizedPreviewAspectRatio("device"))
        assertEquals(20f / 9f, previewAspectRatioValue("device", 20f / 9f), 0.0001f)
        assertEquals(9f / 20f, previewAspectRatioValue("device", 9f / 20f), 0.0001f)
        assertEquals(1f, previewAspectRatioValue("device", Float.NaN), 0f)
    }

    @Test
    fun allPresetsFitPortraitAndLandscapeWithoutDistortion() {
        val ratios = listOf(16f / 9f, 4f / 3f, 1f, 9f / 16f)
        val bounds = listOf(360f to 640f, 640f to 360f, 360f to 200f)

        for ((maxWidth, maxHeight) in bounds) {
            for (ratio in ratios) {
                val size = fitPreviewSize(maxWidth, maxHeight, ratio)
                assertFiniteAndBounded(size, maxWidth, maxHeight)
                assertEquals(ratio, size.width / size.height, 0.0001f)
                assertTrue(size.width == maxWidth || size.height == maxHeight)
            }
        }
    }

    @Test
    fun widePreviewUsesWidthAndTallPreviewUsesHeight() {
        val wide = fitPreviewSize(400f, 300f, 16f / 9f)
        assertEquals(400f, wide.width, 0.001f)
        assertEquals(225f, wide.height, 0.001f)

        val tall = fitPreviewSize(400f, 300f, 9f / 16f)
        assertEquals(168.75f, tall.width, 0.001f)
        assertEquals(300f, tall.height, 0.001f)
    }

    @Test
    fun unavailableOrInvalidBoundsProduceZeroSize() {
        val invalidBounds = listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (bound in invalidBounds) {
            assertEquals(PreviewSize(0f, 0f), fitPreviewSize(bound, 300f, 16f / 9f))
            assertEquals(PreviewSize(0f, 0f), fitPreviewSize(400f, bound, 16f / 9f))
        }
    }

    @Test
    fun invalidRatiosDefaultToSquare() {
        val invalidRatios = listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (ratio in invalidRatios) {
            assertEquals(PreviewSize(300f, 300f), fitPreviewSize(400f, 300f, ratio))
        }
    }

    @Test
    fun extremeFiniteValuesNeverOverflowBounds() {
        val cases = listOf(
            Triple(Float.MAX_VALUE, Float.MAX_VALUE, 16f / 9f),
            Triple(Float.MAX_VALUE, Float.MAX_VALUE, 9f / 16f),
            Triple(Float.MAX_VALUE, 1f, Float.MAX_VALUE),
            Triple(1f, Float.MAX_VALUE, Float.MIN_VALUE),
            Triple(Float.MIN_VALUE, Float.MIN_VALUE, 1f)
        )
        for ((maxWidth, maxHeight, ratio) in cases) {
            val size = fitPreviewSize(maxWidth, maxHeight, ratio)
            assertFiniteAndBounded(size, maxWidth, maxHeight)
            assertTrue(size.width > 0f && size.height > 0f)
        }
    }

    @Test
    fun unrepresentableDimensionProducesZeroSize() {
        assertEquals(PreviewSize(0f, 0f), fitPreviewSize(Float.MIN_VALUE, 1f, Float.MAX_VALUE))
        assertEquals(PreviewSize(0f, 0f), fitPreviewSize(1f, Float.MIN_VALUE, Float.MIN_VALUE))
    }

    private fun assertFiniteAndBounded(size: PreviewSize, maxWidth: Float, maxHeight: Float) {
        assertTrue(size.width.isFinite() && size.height.isFinite())
        assertTrue(size.width in 0f..maxWidth)
        assertTrue(size.height in 0f..maxHeight)
    }
}
