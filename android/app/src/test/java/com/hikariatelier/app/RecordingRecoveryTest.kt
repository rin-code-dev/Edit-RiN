package com.hikariatelier.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

class RecordingRecoveryTest {
    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("recording-recovery-test").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }

    @Test fun finishedCaptureSurvivesAStoreRecreationUntilExplicitDiscard() = withDirectory { root ->
        val bytes = ByteArray(100_000) { (it % 251).toByte() }
        val source = File(root, "transfer.part").apply { writeBytes(bytes) }
        val directory = File(root, "recovery")
        val retained = RecordingRecoveryStore(directory).retain(source, "video/mp4", "my-recording.mp4", 12_345)
        assertFalse(source.exists())
        assertArrayEquals(bytes, retained.file.readBytes())

        val storeAfterRecreation = RecordingRecoveryStore(directory)
        val restored = storeAfterRecreation.restoreAndClean()!!
        assertEquals("video/mp4", restored.mimeType)
        assertEquals("my-recording.mp4", restored.displayName)
        assertEquals(12_345L, restored.durationMillis)
        assertArrayEquals(bytes, restored.file.readBytes())
        storeAfterRecreation.discard(restored)
        assertFalse(restored.file.exists())
        assertNull(storeAfterRecreation.restoreAndClean())
    }

    @Test fun restoredCaptureIsPreservedWhileOrphanedPrivateFilesAreCleaned() = withDirectory { root ->
        val directory = File(root, "recovery")
        val source = File(root, "transfer.part").apply { writeText("complete recording") }
        val store = RecordingRecoveryStore(directory)
        val pending = store.retain(source, "image/gif", "loop.gif", 1500)
        val orphan = File(directory, "old-unowned.mp4").apply { writeText("old bytes") }
        val interruptedMetadata = File(directory, "pending.properties.tmp").apply { writeText("incomplete") }
        val restored = store.restoreAndClean()!!
        assertEquals(pending.file, restored.file)
        assertTrue(pending.file.exists())
        assertFalse(orphan.exists())
        assertFalse(interruptedMetadata.exists())
    }

    @Test fun captureStillRecoversWhenMetadataCouldNotBeWritten() = withDirectory { root ->
        val directory = File(root, "recovery")
        val store = RecordingRecoveryStore(directory)
        val pending = store.retain(File(root, "transfer.part").apply { writeText("recorded bytes") },
            "video/webm", "recording.webm", 1000)
        File(directory, "pending.properties").delete()
        val restored = RecordingRecoveryStore(directory).restoreAndClean()!!
        assertEquals(pending.file, restored.file)
        assertEquals("video/webm", restored.mimeType)
        assertEquals("recorded bytes", restored.file.readText())
    }

    @Test fun retainingAnotherCaptureDoesNotReplaceThePendingCapture() = withDirectory { root ->
        val store = RecordingRecoveryStore(File(root, "recovery"))
        val pending = store.retain(File(root, "first.part").apply { writeText("first") }, "video/mp4", "first.mp4", 1000)
        val second = File(root, "second.part").apply { writeText("second") }
        try { store.retain(second, "video/mp4", "second.mp4", 1000); fail("Pending capture was replaced") }
        catch (_: IllegalStateException) { }
        assertEquals("first.mp4", store.restoreAndClean()!!.displayName)
        assertEquals("first", pending.file.readText())
        assertEquals("second", second.readText())
    }

    @Test fun failedDiscardKeepsTheCaptureAndItsRecoveryMetadata() = withDirectory { root ->
        val directory = File(root, "recovery")
        val store = RecordingRecoveryStore(directory)
        val pending = store.retain(File(root, "transfer.part").apply { writeText("recorded bytes") },
            "video/mp4", "recording.mp4", 1000)
        val cannotDelete = object : File(pending.file.absolutePath) {
            override fun delete(): Boolean = false
        }
        try { store.discard(pending.copy(file = cannotDelete)); fail("Expected failed deletion") }
        catch (_: IllegalStateException) { }
        assertTrue(File(directory, "pending.properties").exists())
        assertEquals(pending.file, store.restoreAndClean()!!.file)
        assertEquals("recorded bytes", pending.file.readText())
    }

    @Test fun completedCaptureCannotBeRecoveredFromAnInterruptedCleanup() = withDirectory { root ->
        val directory = File(root, "recovery")
        val store = RecordingRecoveryStore(directory)
        val pending = store.retain(File(root, "transfer.part").apply { writeText("saved bytes") },
            "video/mp4", "recording.mp4", 1000)
        val retired = File(directory, "${pending.file.name}.saved")
        assertTrue(pending.file.renameTo(retired))
        assertNull(store.restoreAndClean())
        assertFalse(retired.exists())
    }

    @Test fun metadataCannotReferenceAFileOutsideThePrivateDirectory() = withDirectory { root ->
        val outside = File(root, "outside.mp4").apply { writeText("user data") }
        val directory = File(root, "recovery").apply { mkdirs() }
        Properties().apply {
            setProperty("file", "../outside.mp4")
            setProperty("mime", "video/mp4")
            setProperty("name", "outside.mp4")
            setProperty("size", outside.length().toString())
            setProperty("duration", "1000")
        }.also { properties -> File(directory, "pending.properties").outputStream().use { properties.store(it, null) } }
        assertNull(RecordingRecoveryStore(directory).restoreAndClean())
        assertEquals("user data", outside.readText())
    }

    @Test fun partialDestinationIsDeletedAndSourceCanBeRetried() = withDirectory { root ->
        val bytes = ByteArray(130_000) { (it % 127).toByte() }
        val source = File(root, "capture.mp4").apply { writeBytes(bytes) }
        val destination = File(root, "failed.mp4")
        val fileOutput = destination.outputStream()
        val failingOutput = object : OutputStream() {
            override fun write(value: Int) = throw IOException("Provider write failed")
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                fileOutput.write(buffer, offset, minOf(length, 1000))
                throw IOException("Provider write failed")
            }
            override fun close() { fileOutput.close() }
        }
        try {
            copyRecordingToDestination(source, { failingOutput }, { destination.delete() })
            fail("Expected provider write failure")
        } catch (_: IOException) { }
        assertFalse(destination.exists())
        assertArrayEquals(bytes, source.readBytes())

        val retry = ByteArrayOutputStream()
        var deletedSuccessfulDestination = false
        assertEquals(bytes.size.toLong(), copyRecordingToDestination(source, { retry }, { deletedSuccessfulDestination = true }))
        assertFalse(deletedSuccessfulDestination)
        assertArrayEquals(bytes, retry.toByteArray())
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test fun aDestinationThatCannotOpenIsDeletedWithoutLosingTheCapture() = withDirectory { root ->
        val source = File(root, "capture.gif").apply { writeText("GIF bytes") }
        var deletedDestination = false
        try {
            copyRecordingToDestination(source, { null }, { deletedDestination = true })
            fail("Expected unavailable destination")
        } catch (_: IllegalStateException) { }
        assertTrue(deletedDestination)
        assertEquals("GIF bytes", source.readText())
    }
}
