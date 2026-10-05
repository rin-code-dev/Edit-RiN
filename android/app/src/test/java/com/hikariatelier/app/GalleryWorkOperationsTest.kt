package com.hikariatelier.app

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class GalleryWorkOperationsTest {
    @Test fun copiesHaveUniqueNamesAndIndependentMutableProjectConfiguration() {
        val original = Work("source", "Artwork", "code", files = mutableMapOf("helper.mjs" to "helper"),
            previewAspectRatio = "16:9", p5Version = P5_VERSION_LEGACY, p5SoundEnabled = true,
            libraries = mapOf("matter-js" to "0.20.0"), parameterValues = mapOf("speed" to "3"),
            isPinned = true, tags = listOf("test"))
        val copy = copyGalleryWork(original, setOf("Artwork copy", "Artwork copy 2"))
        assertEquals("Artwork copy 3", copy.title)
        assertNotEquals(original.id, copy.id)
        assertEquals(original.libraries.toMap(), copy.libraries)
        assertEquals(original.previewAspectRatio, copy.previewAspectRatio)
        assertEquals(original.p5Version, copy.p5Version)
        assertTrue(copy.p5SoundEnabled)
        copy.files["helper.mjs"] = "changed"
        copy.parameterValues["speed"] = "5"
        copy.tags.clear()
        assertEquals("helper", original.files["helper.mjs"])
        assertEquals("3", original.parameterValues["speed"])
        assertEquals(listOf("test"), original.tags.toList())
    }

    @Test fun restoringKeepsNewerSurvivorsAndRestoresOnlyRemovedIdsAtTheirPositions() {
        val first = Work("one", "One", "newer")
        val added = Work("new", "Added", "new")
        val removed = listOf(RemovedGalleryWork(1, Work("two", "Two", "two")),
            RemovedGalleryWork(2, Work("three", "Three", "three")))
        val restored = restoreGalleryWorks(listOf(first, added), removed)
        assertEquals(listOf("one", "two", "three", "new"), restored.map { it.id })
        assertSame(first, restored.first())
        assertSame(added, restored.last())
        assertNotSame(removed.first().work, restored[1])
    }

    @Test(expected = IllegalArgumentException::class)
    fun restoringNeverOverwritesAnExistingId() {
        restoreGalleryWorks(listOf(Work("one", "External", "keep")),
            listOf(RemovedGalleryWork(0, Work("one", "Deleted", "old"))))
    }

    @Test fun multipleWorksRoundTripThroughZipWithAssetsAndRemappedSnapshotOwners() {
        val directory = Files.createTempDirectory("gallery-export").toFile()
        try {
            val storage = AssetStorage(directory.resolve("assets"))
            val asset = storage.put("media bytes".byteInputStream(), "application/octet-stream")
            val works = listOf(Work("one", "One", "first", assets = mapOf("media.bin" to asset)),
                Work("two", "Two", "second", files = mutableMapOf("helper.js" to "support")))
            val history = WorkSnapshot(code = "previous", files = mapOf("helper.js" to "old"))
            val bytes = ByteArrayOutputStream().also {
                writeAssetBackup(it, works, "one", "{}", storage, mapOf("two" to listOf(history)))
            }.toByteArray()
            val imported = reidentifyGalleryImport(readAssetBackup(bytes.inputStream(), storage))
            assertEquals(listOf("first", "second"), imported.store.works.map { it.code })
            assertTrue(imported.store.works.none { it.id in setOf("one", "two") })
            assertEquals("support", imported.store.works[1].files["helper.js"])
            assertEquals(listOf(history), imported.snapshots[imported.store.works[1].id])
            assertEquals(asset, imported.store.works[0].assets["media.bin"])
            assertTrue(storage.contains(asset))
            assertNull(imported.settings)
        } finally { directory.deleteRecursively() }
    }
}
