package com.hikariatelier.app

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** A distinct, immutable attachment for each share; never a "latest media" lookup. */
internal fun createRecordingShareFile(
    directory: File,
    mimeType: String,
    expectedSize: Long,
    openRecording: () -> InputStream?
): File {
    val extension = when (mimeType) {
        "video/mp4" -> "mp4"
        "image/gif" -> "gif"
        "video/webm" -> "webm"
        else -> throw IOException("Unsupported recording type")
    }
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create share directory")
    val now = System.currentTimeMillis()
    val previous = directory.listFiles().orEmpty().filter {
        it.isFile && it.name.matches(Regex("EditRiN_share_[a-f0-9-]+\\.(mp4|gif|webm)"))
    }
    // Keep recent attachments available to X, including after returning to the editor.
    previous.filter { now - it.lastModified() > 48L * 60 * 60 * 1000 }.forEach { it.delete() }
    val retainedBytes = previous.filter { it.exists() }.sumOf { it.length() }
    val maxBytes = 512L * 1024 * 1024 - retainedBytes
    if (maxBytes <= 0 || expectedSize > maxBytes) throw IOException("Share cache is full")
    val target = File(directory, "EditRiN_share_${UUID.randomUUID()}.$extension")
    try {
        val input = openRecording() ?: throw IOException("Recording is unavailable")
        input.use { source ->
            target.outputStream().use { destination ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count
                    if (total > maxBytes) throw IOException("Share cache is full")
                    destination.write(buffer, 0, count)
                }
                if (total == 0L || (expectedSize > 0L && total != expectedSize)) {
                    throw IOException("Recording size mismatch")
                }
            }
        }
        return target
    } catch (error: Exception) {
        target.delete()
        throw error
    }
}
