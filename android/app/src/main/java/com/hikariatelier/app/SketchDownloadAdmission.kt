package com.hikariatelier.app

import java.io.File
import java.io.IOException

/** Shared by queued picker requests and active writes, including zero-byte files. */
internal class SketchDownloadQuota(
    private val maxFiles: Int = 64,
    private val maxBytes: Long = 256L * 1024 * 1024
) {
    internal class Reservation internal constructor(private val quota: SketchDownloadQuota) : java.io.Closeable {
        override fun close() { quota.release(this) }
    }
    internal data class Usage(val files: Int, val bytes: Long)
    private val reservations = mutableMapOf<Reservation, Long>()
    private var usedBytes = 0L

    init { require(maxFiles > 0 && maxBytes >= 0) }

    @Synchronized fun reserve(bytes: Long): Reservation? {
        if (bytes < 0 || reservations.size >= maxFiles || bytes > maxBytes - usedBytes) return null
        return Reservation(this).also { reservations[it] = bytes; usedBytes += bytes }
    }

    @Synchronized fun release(reservation: Reservation): Boolean {
        val bytes = reservations.remove(reservation) ?: return false
        usedBytes -= bytes
        return true
    }

    @Synchronized fun usage() = Usage(reservations.size, usedBytes)
}

/** Atomically claims a new leaf path; existing files are never opened for writing. */
internal fun createUniqueSketchDownload(directory: File, name: String): File {
    require(name.isNotBlank() && name !in setOf(".", "..") && name.none { it in "/\\" || it.isISOControl() })
    if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) throw IOException("Cannot create download directory")
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    val stem = name.substring(0, dot)
    val extension = name.substring(dot)
    for (index in 0..10_000) {
        val candidate = File(directory, if (index == 0) name else "$stem ($index)$extension")
        if (candidate.createNewFile()) return candidate
    }
    throw IOException("Cannot allocate a unique download name")
}

internal fun copyToUniqueSketchDownload(source: File, directory: File, name: String): File {
    val target = createUniqueSketchDownload(directory, name)
    return try {
        target.outputStream().use { output -> source.inputStream().use { it.copyTo(output, 64 * 1024) } }
        target
    } catch (error: Exception) {
        target.delete()
        throw error
    }
}
