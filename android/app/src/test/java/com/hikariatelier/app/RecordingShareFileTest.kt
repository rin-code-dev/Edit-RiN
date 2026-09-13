package com.hikariatelier.app

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingShareFileTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun successiveSharesContainTheirOwnRecordingAndHaveDifferentNames() {
        val directory = temporary.newFolder()
        val previousBytes = ByteArray(200_000) { 17 }
        val currentBytes = ByteArray(300_000) { 93 }
        val previous = createRecordingShareFile(directory, "video/mp4", previousBytes.size.toLong()) {
            ByteArrayInputStream(previousBytes)
        }
        val current = createRecordingShareFile(directory, "video/mp4", currentBytes.size.toLong()) {
            ByteArrayInputStream(currentBytes)
        }
        assertNotEquals(previous.name, current.name)
        assertArrayEquals(previousBytes, previous.readBytes())
        assertArrayEquals(currentBytes, current.readBytes())
        val gif = createRecordingShareFile(directory, "image/gif", 6) {
            ByteArrayInputStream("GIF89a".toByteArray())
        }
        assertTrue(gif.name.endsWith(".gif"))
        assertEquals("GIF89a", gif.readText())
    }

    @Test fun truncatedRecordingCannotBeSharedAndDoesNotReplacePreviousAttachment() {
        val directory = temporary.newFolder()
        val previous = createRecordingShareFile(directory, "video/mp4", 3) {
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        }
        try {
            createRecordingShareFile(directory, "video/mp4", 100) {
                ByteArrayInputStream(byteArrayOf(4, 5, 6))
            }
            fail("Should reject truncated data")
        } catch (_: IOException) { }
        assertEquals(listOf(previous.name), directory.listFiles()!!.map { it.name })
        assertArrayEquals(byteArrayOf(1, 2, 3), previous.readBytes())
    }

    @Test fun failedReadClosesSourceAndRemovesOnlyPartialAttachment() {
        val directory = temporary.newFolder()
        var closed = false
        val input = object : InputStream() {
            var reads = 0
            override fun read(): Int {
                if (reads++ < 4) return 1
                throw IOException("Read failed")
            }
            override fun close() { closed = true }
        }
        try {
            createRecordingShareFile(directory, "image/gif", 20) { input }
            fail("Should reject failed read")
        } catch (_: IOException) { }
        assertTrue(closed)
        assertTrue(directory.listFiles()!!.isEmpty())
    }
}
