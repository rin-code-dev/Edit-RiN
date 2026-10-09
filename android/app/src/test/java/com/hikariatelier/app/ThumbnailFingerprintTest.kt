package com.hikariatelier.app

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ThumbnailFingerprintTest {
    private val input = PreviewRunInput("one", "work", "background(0);", mapOf("a.js" to "const a=1;", "b.js" to "const b=2;"),
        mapOf("image.png" to ProjectAsset("a".repeat(64), 123, "image/png")), "2.3.4", false, mapOf("matter-js" to "0.20.0"), mapOf("speed" to "1"))
    @Test fun identicalSourcesReuseAcrossRunsAndMapOrder() {
        assertEquals(thumbnailFingerprint(input), thumbnailFingerprint(input.copy(token = "new", workId = "copy",
            files = input.files.toList().reversed().toMap())))
    }
    @Test fun everyRenderingInputInvalidatesTheImage() {
        val variants = listOf(input.copy(source = "background(1);"), input.copy(files = emptyMap()),
            input.copy(assets = emptyMap()), input.copy(p5Version = "1.11.5"), input.copy(soundEnabled = true),
            input.copy(libraries = emptyMap()), input.copy(parameterValues = mapOf("speed" to "2")))
        variants.forEach { assertNotEquals(thumbnailFingerprint(input), thumbnailFingerprint(it)) }
    }
    @Test fun evictionAndReplacementInvalidateAStoredStamp() {
        val dir = Files.createTempDirectory("thumbnail-stamp").toFile()
        try {
            val file = java.io.File(dir, "image.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val hash = thumbnailFingerprint(input)
            thumbnailStampFile(file).writeText("$hash\n${file.length()}\n${file.lastModified()}\n")
            assertTrue(thumbnailCacheMatches(file, hash))
            assertFalse(thumbnailCacheMatches(file, thumbnailFingerprint(input.copy(source = "new"))))
            file.writeBytes(byteArrayOf(4, 5))
            assertFalse(thumbnailCacheMatches(file, hash))
            file.delete()
            assertFalse(thumbnailCacheMatches(file, hash))
        } finally { dir.deleteRecursively() }
    }
}
