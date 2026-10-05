package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class SketchDownloadMetadataTest {
    @Test fun filenamesCannotEscapeTheDownloadDirectory() {
        assertEquals("drawing.png", sketchDownloadMetadata("run", "transfer", "../drawing.png", "")!!.name)
        assertEquals("data.json", sketchDownloadMetadata("run", "transfer", "C:\\data.json", "")!!.name)
        assertEquals("download", sketchDownloadMetadata("run", "transfer", "..", "")!!.name)
        assertEquals("test.txt", sketchDownloadMetadata("run", "transfer", "test\u0000.txt", "")!!.name)
    }

    @Test fun mediaAndArbitraryDataRetainTheirMimeTypes() {
        assertEquals("image/png", sketchDownloadMetadata("run", "transfer", "a.png", "")!!.mime)
        assertEquals("application/json", sketchDownloadMetadata("run", "transfer", "a.json", "")!!.mime)
        assertEquals("application/custom", sketchDownloadMetadata("run", "transfer", "a.custom", "application/custom")!!.mime)
        assertEquals("text/plain", sketchDownloadMetadata("run", "transfer", "a.txt", "text/plain;charset=utf-8")!!.mime)
        assertEquals("application/octet-stream", sketchDownloadMetadata("run", "transfer", "a.custom", "bad\nheader")!!.mime)
    }

    @Test fun rejectedTransferIdentityDoesNotReserveADownload() {
        assertNull(sketchDownloadMetadata("", "transfer", "a.png", ""))
        assertNull(sketchDownloadMetadata("run", "x".repeat(101), "a.png", ""))
    }
}
