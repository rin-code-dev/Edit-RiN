package com.hikariatelier.app

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

const val MAX_ASSET_BYTES = 50L * 1024 * 1024
internal const val MAX_WORK_ASSET_BYTES = 200L * 1024 * 1024
internal const val MAX_BACKUP_BYTES = 512L * 1024 * 1024

/** Immutable references let works share a blob without sharing mutable filenames. */
data class ProjectAsset(val hash: String, val size: Long, val mime: String) {
    init {
        require(hash.matches(Regex("[a-f0-9]{64}")))
        require(size in 0..MAX_ASSET_BYTES)
        require(mime.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")))
    }
}

internal fun validAssetName(name: String): Boolean = name.isNotBlank() && name.length <= 160 &&
    !name.startsWith('.') && name.none { it in "/\\?#%" || it.isISOControl() }

internal fun uniqueAssetName(original: String, existing: Set<String>): String {
    require(validAssetName(original))
    if (original !in existing) return original
    val dot = original.lastIndexOf('.').takeIf { it > 0 } ?: original.length
    val base = original.substring(0, dot).take(140)
    val suffix = original.substring(dot).take(16)
    return generateSequence(2) { it + 1 }.map { "$base-$it$suffix" }.first { it !in existing }
}

internal fun validateAssetSet(assets: Map<String, ProjectAsset>) {
    require(assets.size <= 100)
    require(assets.keys.all(::validAssetName))
    require(assets.values.sumOf { it.size } <= MAX_WORK_ASSET_BYTES)
}

internal fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
    val buffer = ByteArray(32 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buffer)
        if (n < 0) return total
        total += n
        require(total <= limit) { "File too large" }
        output.write(buffer, 0, n)
    }
}

internal class AssetStorage(private val root: File) {
    fun file(asset: ProjectAsset): File = File(root, asset.hash)
    fun contains(asset: ProjectAsset): Boolean = file(asset).let { it.isFile && it.length() == asset.size }

    @Synchronized
    fun prune(keep: Set<String>) {
        root.listFiles().orEmpty().filter { it.name.matches(Regex("[a-f0-9]{64}")) && it.name !in keep }
            .forEach { it.delete() }
    }

    @Synchronized
    fun put(input: InputStream, mime: String, expectedHash: String? = null): ProjectAsset {
        check(root.isDirectory || root.mkdirs())
        val temp = File.createTempFile("import-", ".tmp", root)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val size = temp.outputStream().use { out ->
                java.security.DigestInputStream(input, digest).let { copyBounded(it, out, MAX_ASSET_BYTES) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            require(expectedHash == null || hash == expectedHash) { "Asset checksum mismatch" }
            val asset = ProjectAsset(hash, size, mime)
            if (!contains(asset)) {
                require(root.listFiles().orEmpty().sumOf { it.length() } <= 1024L * 1024 * 1024) { "Asset storage full" }
                check(temp.renameTo(file(asset)))
            }
            return asset
        } finally { temp.delete() }
    }
}

internal data class AssetBackup(val store: WorkStore, val settings: String?)

internal fun writeAssetBackup(output: OutputStream, works: List<Work>, activeId: String, settings: String, storage: AssetStorage) {
    val assets = works.flatMap { it.assets.values }.distinctBy { it.hash }
    require(assets.sumOf { it.size } <= MAX_BACKUP_BYTES)
    ZipOutputStream(output.buffered()).use { zip ->
        fun text(name: String, value: String) {
            zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
        text("works.json", serializeWorkStore(works, activeId))
        text("settings.json", settings)
        assets.forEach { asset ->
            check(storage.contains(asset)) { "Missing asset" }
            zip.putNextEntry(ZipEntry("assets/${asset.hash}"))
            storage.file(asset).inputStream().use { copyBounded(it, zip, MAX_ASSET_BYTES) }
            zip.closeEntry()
        }
    }
}

internal fun readAssetBackup(input: InputStream, storage: AssetStorage): AssetBackup {
    var works: String? = null
    var settings: String? = null
    val seen = mutableSetOf<String>()
    val blobs = mutableMapOf<String, ProjectAsset>()
    var total = 0L
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            require(seen.add(entry.name) && seen.size <= 2000)
            when {
                entry.name == "works.json" || entry.name == "settings.json" -> {
                    val output = java.io.ByteArrayOutputStream()
                    total += copyBounded(zip, output, 16L * 1024 * 1024)
                    val text = output.toString("UTF-8")
                    if (entry.name == "works.json") works = text else settings = text
                }
                entry.name.matches(Regex("assets/[a-f0-9]{64}")) -> {
                    val hash = entry.name.substringAfter('/')
                    val asset = storage.put(zip, "application/octet-stream", hash)
                    blobs[hash] = asset
                    total += asset.size
                }
                entry.isDirectory && entry.name == "assets/" -> Unit
                else -> error("Unsupported backup entry")
            }
            require(total <= MAX_BACKUP_BYTES)
            zip.closeEntry()
        }
    }
    val store = works?.let(::parseWorkStoreJson) ?: error("Invalid works")
    require(store.works.isNotEmpty())
    store.works.flatMap { it.assets.values }.forEach { asset ->
        require(blobs[asset.hash]?.size == asset.size) { "Missing asset in backup" }
    }
    return AssetBackup(store, settings)
}

internal fun assetByteRange(header: String?, size: Long): LongRange? {
    if (header == null || size <= 0) return null
    val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(header) ?: return null
    val (first, last) = match.destructured
    if (first.isEmpty()) {
        val suffix = last.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return maxOf(0L, size - suffix)..(size - 1)
    }
    val start = first.toLongOrNull()?.takeIf { it < size } ?: return null
    val end = if (last.isEmpty()) size - 1 else last.toLongOrNull()?.coerceAtMost(size - 1) ?: return null
    return if (end >= start) start..end else null
}

internal fun snapshotWork(work: Work, assets: Map<String, ProjectAsset> = work.assets.toMap()): Work = Work(
    id = work.id, title = work.title, code = work.code, files = work.files.toMutableMap(), assets = assets,
    revisions = work.revisions.toMutableList(), previewAspectRatio = work.previewAspectRatio,
    p5Version = work.p5Version, p5SoundEnabled = work.p5SoundEnabled,
    createdAt = work.createdAt, updatedAt = work.updatedAt
)

internal fun assetMimeType(name: String, provided: String?): String {
    val known = mapOf("png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif",
        "webp" to "image/webp", "svg" to "image/svg+xml", "mp3" to "audio/mpeg", "wav" to "audio/wav",
        "ogg" to "audio/ogg", "m4a" to "audio/mp4", "mp4" to "video/mp4", "webm" to "video/webm",
        "ttf" to "font/ttf", "otf" to "font/otf", "woff" to "font/woff", "woff2" to "font/woff2",
        "json" to "application/json", "csv" to "text/csv", "txt" to "text/plain", "obj" to "text/plain",
        "mtl" to "text/plain", "vert" to "text/plain", "frag" to "text/plain", "glsl" to "text/plain")
    return known[name.substringAfterLast('.', "").lowercase()]
        ?: provided?.takeIf { it.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")) }
        ?: "application/octet-stream"
}

internal class LimitedAssetStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
    override fun skip(count: Long): Long {
        val skipped = `in`.skip(minOf(count.coerceAtLeast(0), remaining))
        remaining -= skipped
        return skipped
    }
    override fun available(): Int = minOf(`in`.available().toLong(), remaining).toInt()
    override fun read(): Int {
        if (remaining <= 0) return -1
        val value = super.read()
        if (value >= 0) remaining--
        return value
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining <= 0) return -1
        val count = `in`.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count > 0) remaining -= count
        return count
    }
}
