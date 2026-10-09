package com.hikariatelier.app

import java.io.File
import java.security.MessageDigest
import java.nio.ByteBuffer

/** No install/version timestamps or run tokens: identical authored content can reuse an image. */
internal fun thumbnailFingerprint(input: PreviewRunInput): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun add(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes)
    }
    fun addMap(values: Map<String, String>) {
        add(values.size.toString())
        values.toSortedMap().forEach { (key, value) -> add(key); add(value) }
    }
    add("thumbnail-renderer-3"); add(input.source); add(input.p5Version); add(input.soundEnabled.toString())
    addMap(input.files); addMap(input.libraries); addMap(input.parameterValues)
    addMap(input.assets.mapValues { (_, asset) -> "${asset.hash}:${asset.size}:${asset.mime}" })
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun thumbnailStampFile(file: File) = File(file.parentFile, "${file.name}.stamp")

internal fun thumbnailCacheMatches(file: File, fingerprint: String): Boolean = runCatching {
    val stamp = thumbnailStampFile(file).readLines()
    file.length() > 0 && stamp == listOf(fingerprint, file.length().toString(), file.lastModified().toString())
}.getOrDefault(false)
