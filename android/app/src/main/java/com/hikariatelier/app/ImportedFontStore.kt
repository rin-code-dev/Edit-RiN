package com.hikariatelier.app

import android.graphics.Typeface
import java.io.File
import java.io.InputStream
import java.util.UUID

internal data class ImportedFont(val fileName: String, val typeface: Typeface)

internal fun importFont(directory: File, source: InputStream): ImportedFont {
    check(directory.isDirectory || directory.mkdirs())
    val destination = File(directory, "imported-${UUID.randomUUID()}.font")
    try {
        source.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= 20L * 1024 * 1024) { "Font exceeds 20 MB" }
                    output.write(buffer, 0, read)
                }
                require(total >= 12) { "Invalid font" }
            }
        }
        val signature = destination.inputStream().use { input ->
            val bytes = ByteArray(4)
            check(input.read(bytes) == 4)
            bytes
        }
        require(signature.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
            String(signature, Charsets.US_ASCII) in listOf("OTTO", "true", "ttcf")) {
            "Use TTF, OTF or TTC"
        }
        return ImportedFont(destination.name, Typeface.createFromFile(destination))
    } catch (error: Exception) {
        destination.delete() // This attempt's private, unpublished copy only.
        throw error
    }
}

internal fun loadImportedFont(directory: File, name: String?): Typeface? {
    if (name == null || !Regex("imported-[a-f0-9-]+\\.font").matches(name)) return null
    return runCatching { Typeface.createFromFile(File(directory, name)) }.getOrNull()
}
