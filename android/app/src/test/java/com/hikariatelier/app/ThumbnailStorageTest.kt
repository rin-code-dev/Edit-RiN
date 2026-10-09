package com.hikariatelier.app

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbnailStorageTest {
    private fun png(color: Int): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
        output.toByteArray()
    }
    @Test fun lateBackgroundImageCannotReplaceForegroundCapture() = runBlocking {
        val dir = Files.createTempDirectory("thumbnail-publish").toFile()
        try {
            val file = workPreviewFile(dir, "work")
            assertTrue(storeWorkPreviewBytes(file, png(-1), 0L, "saved"))
            // A device clock change or a rapid earlier write must not make the next timestamp go backwards.
            file.setLastModified(System.currentTimeMillis() + 5000)
            val checkpoint = file.lastModified()
            val foreground = png(-65536)
            assertTrue(storeWorkPreviewBytes(file, foreground, fingerprint = "foreground"))
            assertTrue(file.lastModified() > checkpoint)
            assertFalse(storeWorkPreviewBytes(file, png(-16777216), checkpoint, "late"))
            assertArrayEquals(foreground, file.readBytes())
            assertTrue(thumbnailCacheMatches(file, "foreground"))
            assertFalse(thumbnailCacheMatches(file, "late"))
        } finally { dir.deleteRecursively() }
    }
    @Test fun malformedCaptureKeepsExistingImageAndStamp() = runBlocking {
        val dir = Files.createTempDirectory("thumbnail-invalid").toFile()
        try {
            val file = workPreviewFile(dir, "work"); val image = png(-1)
            assertTrue(storeWorkPreviewBytes(file, image, fingerprint = "saved"))
            assertFalse(storeWorkPreviewBytes(file, byteArrayOf(1, 2, 3), fingerprint = "invalid"))
            assertArrayEquals(image, file.readBytes()); assertTrue(thumbnailCacheMatches(file, "saved"))
        } finally { dir.deleteRecursively() }
    }
    @Test fun transparentCaptureKeepsExistingImageButSolidColorsAreValid() = runBlocking {
        val dir = Files.createTempDirectory("thumbnail-empty").toFile()
        try {
            val file = workPreviewFile(dir, "work"); val image = png(-1)
            assertTrue(storeWorkPreviewBytes(file, image, fingerprint = "saved"))
            assertFalse(storeWorkPreviewBytes(file, png(0), fingerprint = "empty"))
            assertArrayEquals(image, file.readBytes()); assertTrue(thumbnailCacheMatches(file, "saved"))
            assertTrue(storeWorkPreviewBytes(file, png(-16777216), fingerprint = "black"))
            assertTrue(thumbnailCacheMatches(file, "black"))
            assertTrue(storeWorkPreviewBytes(file, png(0x01000000), fingerprint = "faint"))
        } finally { dir.deleteRecursively() }
    }
}
