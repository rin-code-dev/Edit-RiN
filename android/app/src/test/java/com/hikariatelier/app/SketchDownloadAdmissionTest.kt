package com.hikariatelier.app

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class SketchDownloadAdmissionTest {
    @Test fun defaultByteLimitIncludesAllWaitingAndWritingReservations() {
        val quota = SketchDownloadQuota()
        val full = checkNotNull(quota.reserve(256L * 1024 * 1024))
        assertNull(quota.reserve(1))
        val empty = checkNotNull(quota.reserve(0))
        full.close()
        assertEquals(SketchDownloadQuota.Usage(1, 0), quota.usage())
        assertNotNull(quota.reserve(256L * 1024 * 1024))
        empty.close()
    }

    @Test fun zeroByteFilesStillRespectTheFileLimitAndReleaseIsIdempotent() {
        val quota = SketchDownloadQuota()
        val reservations = List(64) { checkNotNull(quota.reserve(0)) }
        assertNull(quota.reserve(0))
        assertEquals(SketchDownloadQuota.Usage(64, 0), quota.usage())
        assertTrue(quota.release(reservations.first()))
        assertFalse(quota.release(reservations.first()))
        assertNotNull(quota.reserve(0))
        assertEquals(SketchDownloadQuota.Usage(64, 0), quota.usage())
    }

    @Test fun totalBytesAreBoundedWithoutOverflowAndCapacityReturnsAfterFailure() {
        val quota = SketchDownloadQuota(maxFiles = 4, maxBytes = Long.MAX_VALUE)
        val first = checkNotNull(quota.reserve(Long.MAX_VALUE - 1))
        assertNull(quota.reserve(2))
        assertNull(quota.reserve(-1))
        checkNotNull(quota.reserve(1)).use {
            assertEquals(Long.MAX_VALUE, quota.usage().bytes)
        }
        try {
            first.use { throw IOException("writer failed") }
        } catch (_: IOException) { }
        assertEquals(SketchDownloadQuota.Usage(0, 0), quota.usage())
        assertNotNull(quota.reserve(Long.MAX_VALUE))
    }

    @Test fun concurrentAdmissionsShareOneQuota() {
        val quota = SketchDownloadQuota(maxFiles = 8, maxBytes = 100)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = List(64) { executor.submit<SketchDownloadQuota.Reservation?> { quota.reserve(20) } }.map { it.get() }
            assertEquals(5, results.count { it != null })
            assertEquals(SketchDownloadQuota.Usage(5, 100), quota.usage())
            results.filterNotNull().forEach { it.close() }
            assertEquals(SketchDownloadQuota.Usage(0, 0), quota.usage())
        } finally { executor.shutdownNow() }
    }

    @Test fun legacySavesNeverOverwriteExistingNamesAndEmptyFilesAreValid() {
        val root = Files.createTempDirectory("sketch-download").toFile()
        try {
            val destination = File(root, "Downloads/Edit-RiN")
            val original = File(destination.apply { mkdirs() }, "frames.json").apply { writeText("original") }
            val source = File(root, "source").apply { writeText("new") }
            val saved = copyToUniqueSketchDownload(source, destination, "frames.json")
            assertEquals("frames (1).json", saved.name)
            assertEquals("original", original.readText())
            assertEquals("new", saved.readText())
            source.writeText("")
            val empty = copyToUniqueSketchDownload(source, destination, "frames.json")
            assertEquals("frames (2).json", empty.name)
            assertEquals(0L, empty.length())
        } finally { root.deleteRecursively() }
    }

    @Test fun failedCopyRemovesOnlyItsOwnNewFileAndReleasesItsReservation() {
        val root = Files.createTempDirectory("sketch-download-failure").toFile()
        val quota = SketchDownloadQuota()
        try {
            val destination = File(root, "downloads").apply { mkdirs() }
            val original = File(destination, "drawing.png").apply { writeText("original") }
            try {
                checkNotNull(quota.reserve(20)).use {
                    copyToUniqueSketchDownload(File(root, "missing-source"), destination, "drawing.png")
                }
                fail("Missing source must fail")
            } catch (_: IOException) { }
            assertEquals("original", original.readText())
            assertEquals(listOf("drawing.png"), destination.listFiles()!!.map { it.name })
            assertEquals(SketchDownloadQuota.Usage(0, 0), quota.usage())
        } finally { root.deleteRecursively() }
    }

    @Test fun parallelLegacyWritersClaimDifferentFiles() {
        val root = Files.createTempDirectory("sketch-download-parallel").toFile()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val files = List(16) { executor.submit<File> { createUniqueSketchDownload(root, "frame.png") } }.map { it.get() }
            assertEquals(16, files.map { it.name }.distinct().size)
            assertTrue(files.all { it.exists() && it.length() == 0L })
        } finally { executor.shutdownNow(); root.deleteRecursively() }
    }
}
