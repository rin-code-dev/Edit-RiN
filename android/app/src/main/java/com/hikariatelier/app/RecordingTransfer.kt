package com.hikariatelier.app

import android.util.Base64
import java.io.File
import java.io.FileOutputStream

/** Only one capture owns this bounded, temporary stream at a time. */
internal class RecordingTransfer(private val directory: File) {
    private var id = ""
    private var file: File? = null
    private var output: FileOutputStream? = null
    private var bytes = 0L

    @Synchronized fun begin(token: String): Boolean {
        if (output != null || token.isBlank() || token.length > 100) return false
        id = token
        return runCatching {
            directory.mkdirs()
            file = File.createTempFile("recording-", ".part", directory)
            output = FileOutputStream(file!!)
            bytes = 0
            true
        }.getOrElse { abort(token); false }
    }

    @Synchronized fun append(token: String, encoded: String): Boolean {
        if (token != id || output == null) return false
        return runCatching {
            require(encoded.length <= 350_000)
            val chunk = Base64.decode(encoded, Base64.DEFAULT)
            require(bytes + chunk.size <= 256L * 1024 * 1024)
            output!!.write(chunk)
            bytes += chunk.size
            true
        }.getOrElse { abort(token); false }
    }

    @Synchronized fun finish(token: String): File? {
        if (token != id || output == null) return null
        return try {
            output!!.close()
            check(bytes > 0)
            file.also { file = null; output = null; id = "" }
        } catch (_: Exception) { abort(token); null }
    }

    @Synchronized fun abort(token: String = id) {
        if (token != id) return
        runCatching { output?.close() }
        file?.delete()
        file = null
        output = null
        id = ""
    }
}
