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
    @Test fun rejectsUnsafeArchivePathsAndOversizedStreams() {
        assertTrue(runCatching { readAssetBackup(ByteArrayInputStream(zip("../escape" to byteArrayOf(1))), storage()) }.isFailure)
        assertTrue(runCatching { copyBounded(ByteArrayInputStream(ByteArray(11)), ByteArrayOutputStream(), 10) }.isFailure)
        val work = Work("a", "A", "")
        val json = serializeWorkStore(listOf(work), "a").replace("\"assets\": {}", "\"assets\": {\"../x\":{\"hash\":\"${"a".repeat(64)}\",\"size\":1,\"mime\":\"image/png\"}}")
        assertNull(parseWorkStoreJson(json))
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
}
