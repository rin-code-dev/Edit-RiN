package com.hikariatelier.app

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RecordingTransferTest {
    @Test fun emptyDownloadIsValidButEmptyCaptureIsRejected() {
        val directory = Files.createTempDirectory("rin-transfer-test").toFile()
        try {
            val capture = RecordingTransfer(directory)
            assertTrue(capture.begin("capture"))
            assertNull(capture.finish("capture"))
            assertTrue(directory.listFiles().orEmpty().isEmpty())

            val download = RecordingTransfer(directory, allowEmpty = true)
            assertTrue(download.begin("download"))
            assertFalse(download.begin("overlap"))
            assertNull(download.finish("obsolete"))
            val output = requireNotNull(download.finish("download"))
            assertTrue(output.exists())
            assertEquals(0L, output.length())
            output.delete()
        } finally { directory.deleteRecursively() }
    }
}
