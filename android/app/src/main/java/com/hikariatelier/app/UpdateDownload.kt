package com.hikariatelier.app

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun copyUpdatePayload(input: InputStream, output: OutputStream, asset: ReleaseAsset,
                                       progress: suspend (Float) -> Unit) {
    require(asset.size in 1..MAX_UPDATE_BYTES)
    val digest = MessageDigest.getInstance("SHA-256")
    var bytes = 0L
    var lastPercent = -1
    val buffer = ByteArray(64 * 1024)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        bytes += count
        check(bytes <= asset.size && bytes <= MAX_UPDATE_BYTES)
        output.write(buffer, 0, count)
        digest.update(buffer, 0, count)
        val percent = (bytes * 100 / asset.size).toInt()
        if (percent != lastPercent) {
            lastPercent = percent
            progress(bytes.toFloat() / asset.size)
        }
    }
    check(bytes == asset.size)
    val hash = digest.digest().joinToString("") { "%02x".format(it) }
    check(asset.sha256 == null || hash == asset.sha256)
}
