package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class EditorImprovementsTest {
    @Test fun differenceShowsOnlyChangedRegion() {
        val difference = revisionDifference("setup\nold\nend", "setup\nnew\nend")
        assertTrue(difference.contains("− old"))
        assertTrue(difference.contains("+ new"))
        assertFalse(difference.contains("setup"))
        assertFalse(difference.contains("end"))
        assertEquals("@@ 4 @@\n", revisionDifference("a\nb\nc", "a\nb\nc"))
    }

    @Test fun assetExpressionEscapesQuotesAndSelectsLoader() {
        val asset = ProjectAsset("a".repeat(64), 0, "image/png")
        assertEquals("loadImage(\"assets/a\\\"b.png\")", assetLoaderCode("a\"b.png", asset))
        assertEquals("loadSound(\"assets/a.mp3\")", assetLoaderCode("a.mp3", asset.copy(mime = "audio/mpeg")))
    }

    @Test fun singleWorkZipPreservesFilesAssetsAndRuntime() {
        val directory = Files.createTempDirectory("rin-work-zip").toFile()
        try {
            val storage = AssetStorage(directory)
            val asset = storage.put(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "image/png")
            val work = Work("work", "Title", "draw()", files = mutableMapOf("helper.js" to "let x = 1"),
                assets = mapOf("a.png" to asset), p5Version = P5_VERSION_LEGACY, p5SoundEnabled = true)
            val output = ByteArrayOutputStream()
            writeAssetBackup(output, listOf(work), work.id, "{}", storage)
            val restored = readAssetBackup(ByteArrayInputStream(output.toByteArray()), storage).store.works.single()
            assertEquals(work.code, restored.code)
            assertEquals(work.files.toMap(), restored.files.toMap())
            assertEquals(work.assets.toMap(), restored.assets.toMap())
            assertEquals(P5_VERSION_LEGACY, restored.p5Version)
            assertTrue(restored.p5SoundEnabled)
        } finally { directory.deleteRecursively() }
    }
}
