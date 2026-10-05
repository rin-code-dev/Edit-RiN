package com.hikariatelier.app

import java.io.File
import java.io.OutputStream
import java.util.Properties
import java.util.UUID

internal data class PendingRecording(
    val file: File,
    val mimeType: String,
    val displayName: String,
    val durationMillis: Long
) {
    val sizeBytes: Long get() = file.length()
}

/** One finished capture may await an explicit save or discard, including after recreation. */
internal class RecordingRecoveryStore(private val directory: File) {
    private val metadata get() = File(directory, "pending.properties")

    fun restoreAndClean(): PendingRecording? {
        val pending = runCatching {
            val properties = Properties().apply { metadata.inputStream().use(::load) }
            val source = File(directory, properties.getProperty("file"))
            require(source.canonicalFile.parentFile == directory.canonicalFile)
            val mime = properties.getProperty("mime")
            require(recordingExtension(mime) != null)
            require(source.isFile && source.length() in 1..MAX_RECORDING_BYTES)
            require(source.length() == properties.getProperty("size").toLong())
            PendingRecording(source, mime, properties.getProperty("name"),
                properties.getProperty("duration").toLong().coerceAtLeast(0L))
        }.getOrNull() ?: directory.listFiles()?.filter { file ->
            file.isFile && file.name.matches(Regex("[a-f0-9-]{36}\\.(mp4|webm|gif)")) &&
                file.length() in 1..MAX_RECORDING_BYTES
        }?.maxByOrNull { it.lastModified() }?.let { file ->
            // Disk-full can prevent writing metadata after a successful rename. Keep that video too.
            val mime = when (file.extension) { "gif" -> "image/gif"; "webm" -> "video/webm"; else -> "video/mp4" }
            PendingRecording(file, mime, "EditRiN_${file.name}", 0)
        }
        // Only this store's private files are disposable. The one referenced capture survives.
        directory.listFiles()?.filter { it != pending?.file && it != metadata }?.forEach { it.delete() }
        if (pending == null) metadata.delete()
        return pending
    }

    fun retain(file: File, mimeType: String, displayName: String, durationMillis: Long): PendingRecording {
        val extension = recordingExtension(mimeType) ?: error("Unsupported recording format")
        require(file.isFile && file.length() in 1..MAX_RECORDING_BYTES)
        check(!metadata.exists()) { "A recording is already awaiting save" }
        directory.mkdirs()
        val target = File(directory, "${UUID.randomUUID()}.$extension")
        // A rename consumes no additional video-sized storage. If it fails, preserve the source.
        val source = if (file.renameTo(target)) target else file
        val pending = PendingRecording(source, mimeType, displayName, durationMillis)
        if (source == target) {
            val temporaryMetadata = File(directory, "pending.properties.tmp")
            runCatching {
                Properties().apply {
                    setProperty("file", source.name)
                    setProperty("mime", mimeType)
                    setProperty("name", displayName)
                    setProperty("duration", durationMillis.toString())
                    setProperty("size", source.length().toString())
                }.also { properties -> temporaryMetadata.outputStream().use { properties.store(it, null) } }
                check(temporaryMetadata.renameTo(metadata))
            }
            temporaryMetadata.delete()
        }
        return pending
    }

    fun discard(pending: PendingRecording) {
        check(pending.file.delete() || !pending.file.exists()) { "Could not discard recording" }
        metadata.delete()
    }

    /** Completed output is authoritative even if best-effort source cleanup is interrupted. */
    fun complete(pending: PendingRecording) {
        val retired = File(pending.file.parentFile, "${pending.file.name}.saved")
        if (pending.file.renameTo(retired)) {
            // A .saved source is excluded from the UUID fallback on the next launch.
            metadata.delete()
            retired.delete()
        } else discard(pending)
    }

    companion object {
        const val MAX_RECORDING_BYTES = 256L * 1024 * 1024
    }
}

internal fun recordingExtension(mimeType: String): String? = when (mimeType) {
    "video/mp4" -> "mp4"
    "video/webm" -> "webm"
    "image/gif" -> "gif"
    else -> null
}

/** Failed destinations are removed; the original capture is always retained by the caller. */
internal fun copyRecordingToDestination(
    source: File,
    openOutput: () -> OutputStream?,
    deleteFailedDestination: () -> Unit
): Long {
    try {
        val expected = source.length()
        require(source.isFile && expected in 1..RecordingRecoveryStore.MAX_RECORDING_BYTES)
        val copied = (openOutput() ?: error("保存先を開けませんでした")).use { output ->
            source.inputStream().use { it.copyTo(output, 64 * 1024) }
        }
        check(copied == expected && source.length() == expected) { "録画ファイルが変更されました" }
        return copied
    } catch (error: Exception) {
        runCatching(deleteFailedDestination)
        throw error
    }
}
