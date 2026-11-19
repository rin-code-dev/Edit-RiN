package com.hikariatelier.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryLayoutTest {
    @Test fun landscapeAddsColumnsWithoutStretchingTwoCardsAcrossAWideScreen() {
        assertEquals(3, galleryColumnCount(640f, 1f, true))
        assertEquals(4, galleryColumnCount(800f, 1f, true))
        assertEquals(6, galleryColumnCount(1200f, 1f, true))
    }

    @Test fun largerTextReducesColumnsAndKeepsCardsAboveTheirMinimumWidth() {
        for (width in listOf(480f, 640f, 800f, 1200f)) {
            for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
                val columns = galleryColumnCount(width, scale, true)
                assertTrue(columns <= galleryColumnCount(width, 1f, true))
                val cardWidth = (width - 32f - 14f * (columns - 1)) / columns
                if (columns > 1) assertTrue(cardWidth >= 160f * scale)
            }
        }
        assertEquals(2, galleryColumnCount(800f, 2f, true))
    }

    @Test fun portraitKeepsItsExistingTwoColumnsUntilLargerTextNeedsSpace() {
        assertEquals(2, galleryColumnCount(360f, 1f, false))
        assertEquals(2, galleryColumnCount(360f, 1.3f, false))
        assertEquals(1, galleryColumnCount(360f, 1.5f, false))
        assertEquals(2, galleryColumnCount(400f, 1.5f, false))
    }

    @Test fun invalidAndVerySmallSizesAlwaysHaveAtLeastOneColumn() {
        for (width in listOf(Float.NaN, Float.POSITIVE_INFINITY, -10f, 0f, 1f, 100f)) {
            assertEquals(1, galleryColumnCount(width, 1f, true))
        }
        assertEquals(galleryColumnCount(800f, 1f, true), galleryColumnCount(800f, Float.NaN, true))
        assertEquals(1, galleryColumnCount(800f, Float.MAX_VALUE, true))
    }

    @Test fun landscapeSquareGridUsesCompactContactSheetColumnsAndScalesWithFont() {
        assertEquals(3, landscapeSquareColumnCount(480f, 1f))
        assertEquals(4, landscapeSquareColumnCount(600f, 1f))
        assertEquals(5, landscapeSquareColumnCount(740f, 1f))
        for (width in listOf(480f, 600f, 740f, 1000f)) {
            for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
                val columns = landscapeSquareColumnCount(width, scale)
                assertTrue(columns <= landscapeSquareColumnCount(width, 1f))
                val cardWidth = (width - 4f - 14f * (columns - 1)) / columns
                if (columns > 1) assertTrue(cardWidth >= 128f * scale)
            }
        }
        assertEquals(1, landscapeSquareColumnCount(0f, 1f))
        assertEquals(1, landscapeSquareColumnCount(Float.NaN, 1f))
    }

    @Test fun landscapeSampleGridUsesTwoToThreeReadableHorizontalColumns() {
        assertEquals(1, landscapeSampleColumnCount(420f, 1f))
        assertEquals(2, landscapeSampleColumnCount(480f, 1f))
        assertEquals(2, landscapeSampleColumnCount(600f, 1f))
        assertEquals(3, landscapeSampleColumnCount(740f, 1f))
        assertEquals(1, landscapeSampleColumnCount(600f, 1.4f))
        assertEquals(1, landscapeSampleColumnCount(0f, 1f))
    }
}
