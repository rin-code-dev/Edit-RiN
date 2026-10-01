package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectAssetsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun storage() = AssetStorage(temporary.newFolder())
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> entries.forEach { (name, bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
    }.toByteArray()

    @Test fun namesStayWithinAssetDirectoryAndDuplicatesAreRenamed() {
        for (name in listOf("../x", "a/b", "a\\b", ".", "", "a%2fb", "a?x", "a#x", "a\n")) assertFalse(name, validAssetName(name))
        assertTrue(validAssetName("空の写真.png"))
        assertEquals("photo-3.png", uniqueAssetName("photo.png", setOf("photo.png", "photo-2.png")))
    }
    @Test fun binaryAssetsRoundTripWithoutSharingMutableNames() {
        val source = storage()
        val binary = ByteArray(8192) { (it % 256).toByte() }
        val asset = source.put(ByteArrayInputStream(binary), "image/png")
        val work = Work("a", "A", "", assets = mapOf("photo.png" to asset))
        val duplicate = snapshotWork(work)
        duplicate.assets.remove("photo.png")
        assertTrue(work.assets.containsKey("photo.png"))
        val bytes = ByteArrayOutputStream()
        writeAssetBackup(bytes, listOf(work), "a", "{}", source)
        val target = storage()
        val restored = readAssetBackup(ByteArrayInputStream(bytes.toByteArray()), target)
        assertEquals(asset, restored.store.works.single().assets["photo.png"])
        assertArrayEquals(binary, target.file(asset).readBytes())
    }
    @Test fun legacyBackupStillLoadsAndMissingOrCorruptAssetIsRejected() {
        val target = storage()
        val work = Work("a", "A", "")
        assertEquals("a", readAssetBackup(ByteArrayInputStream(zip("works.json" to serializeWorkStore(listOf(work), "a").toByteArray())), target).store.activeWorkId)
        val asset = target.put(ByteArrayInputStream(byteArrayOf(1,2,3)), "image/png")
        work.assets["photo.png"] = asset
        val manifest = serializeWorkStore(listOf(work), "a").toByteArray()
        assertTrue(runCatching { readAssetBackup(ByteArrayInputStream(zip("works.json" to manifest)), target) }.isFailure)
        assertTrue(runCatching { readAssetBackup(ByteArrayInputStream(zip("works.json" to manifest, "assets/${asset.hash}" to byteArrayOf(4,5,6))), target) }.isFailure)
    }
    @Test fun rejectedBackupDoesNotPublishStagedAssets() {
        val target = storage()
        val bytes = byteArrayOf(7, 8, 9)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val asset = ProjectAsset(hash, bytes.size.toLong(), "image/png")
        val invalid = zip("assets/$hash" to bytes, "works.json" to "{broken".toByteArray())
        assertTrue(runCatching { readAssetBackup(ByteArrayInputStream(invalid), target) }.isFailure)
        assertFalse(target.contains(asset))
    }
    @Test fun rejectsUnsafeArchivePathsAndOversizedStreams() {
        assertTrue(runCatching { readAssetBackup(ByteArrayInputStream(zip("../escape" to byteArrayOf(1))), storage()) }.isFailure)
        assertTrue(runCatching { copyBounded(ByteArrayInputStream(ByteArray(11)), ByteArrayOutputStream(), 10) }.isFailure)
        val work = Work("a", "A", "")
        val json = org.json.JSONObject(serializeWorkStore(listOf(work), "a"))
        json.getJSONArray("works").getJSONObject(0).getJSONObject("assets").put(
            "../x", org.json.JSONObject().put("hash", "a".repeat(64)).put("size", 1).put("mime", "image/png"))
        assertNull(parseWorkStoreJson(json.toString()))
    }
    @Test fun pruningRetainsReferencedContent() {
        val store = storage()
        val used = store.put(ByteArrayInputStream(byteArrayOf(1)), "text/plain")
        val unused = store.put(ByteArrayInputStream(byteArrayOf(2)), "text/plain")
        store.prune(setOf(used.hash))
        assertTrue(store.contains(used))
        assertFalse(store.contains(unused))
    }
    @Test fun mediaStreamNeverReadsBeyondRequestedRangeEvenAfterSkipping() {
        val input = LimitedAssetStream(ByteArrayInputStream(byteArrayOf(1,2,3,4,5)), 3)
        assertEquals(2L, input.skip(2))
        assertEquals(1, input.available())
        assertEquals(3, input.read())
        assertEquals(-1, input.read())
        assertEquals(0L, input.skip(10))
    }
    @Test fun supportsMediaRangesAndRejectsInvalidRequests() {
        assertEquals(0L..9L, assetByteRange("bytes=0-", 10))
        assertEquals(2L..4L, assetByteRange("bytes=2-4", 10))
        assertEquals(7L..9L, assetByteRange("bytes=-3", 10))
        for (header in listOf("bytes=10-", "bytes=5-2", "bytes=0-1,3-4", "bytes=-0", "bytes=x-")) assertNull(assetByteRange(header, 10))
    }
    @Test fun fullBackupCarriesIndependentTemplatesAndTheirBinaryAssets() {
        val workStorage = storage(); val templateStorage = storage()
        val bytes = byteArrayOf(3, 9, 27, 81)
        val asset = templateStorage.put(ByteArrayInputStream(bytes), "image/png")
        val work = Work("a", "A", "main")
        val template = Work("template-a", "Image template", "loadImage('image.png')",
            files = mutableMapOf("helper.js" to "helper"), assets = mapOf("image.png" to asset),
            previewAspectRatio = "9:16", parameterValues = mapOf("speed" to "2"))
        val output = ByteArrayOutputStream()
        writeAssetBackup(output, listOf(work), "a", "{}", workStorage,
            templates = listOf(template), templateStorage = templateStorage)
        val restoredStorage = storage()
        val restored = readAssetBackup(ByteArrayInputStream(output.toByteArray()), restoredStorage)
        assertEquals("template-a", restored.templates!!.single().id)
        assertEquals("helper", restored.templates!!.single().files["helper.js"])
        assertEquals("9:16", restored.templates!!.single().previewAspectRatio)
        assertEquals("2", restored.templates!!.single().parameterValues["speed"])
        assertArrayEquals(bytes, restoredStorage.file(asset).readBytes())
    }

    @Test fun legacyBackupOmitsTemplatesWhileExplicitEmptyTemplateListRoundTrips() {
        val work = Work("a", "A", "main")
        val legacy = readAssetBackup(ByteArrayInputStream(zip("works.json" to serializeWorkStore(listOf(work), "a").toByteArray())), storage())
        assertNull(legacy.templates)
        val output = ByteArrayOutputStream()
        writeAssetBackup(output, listOf(work), "a", "{}", storage(), templates = emptyList())
        assertEquals(emptyList<Work>(), readAssetBackup(ByteArrayInputStream(output.toByteArray()), storage()).templates)
    }

    @Test fun missingTemplateBlobRejectsEntireArchiveBeforePublishingWorkAssets() {
        val source = storage(); val target = storage()
        val bytes = byteArrayOf(2, 4, 6)
        val asset = source.put(ByteArrayInputStream(bytes), "image/png")
        val work = Work("a", "A", "main", assets = mapOf("image.png" to asset))
        val template = Work("t", "Template", "", assets = mapOf("image.png" to asset))
        val archive = zip("works.json" to serializeWorkStore(listOf(work), "a").toByteArray(),
            "templates.json" to serializeWorkStore(listOf(template), "").toByteArray(),
            "assets/${asset.hash}" to bytes)
        assertTrue(runCatching { readAssetBackup(ByteArrayInputStream(archive), target) }.isFailure)
        assertFalse(target.contains(asset))
    }

    @Test fun backupExportRejectsDeferredBodiesInsteadOfWritingEmptyCode() {
        val deferred = Work("a", "A", "").also { it.bodyLoaded = false }
        assertTrue(runCatching { writeAssetBackup(ByteArrayOutputStream(), listOf(deferred), "a", "{}", storage()) }.isFailure)
    }

}
