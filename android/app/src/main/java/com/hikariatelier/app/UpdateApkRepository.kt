package com.hikariatelier.app

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Downloads only to a private staging file; an incomplete file is never offered to Android. */
internal class UpdateApkRepository(private val context: Context) {
    private val directory get() = File(context.cacheDir, "app-updates")

    suspend fun download(release: AppRelease, progress: suspend (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val asset = requireNotNull(release.apk)
        check(directory.isDirectory || directory.mkdirs())
        val partial = File(directory, "update.part")
        val ready = File(directory, "update.apk")
        ready.delete()
        var connection: HttpURLConnection? = null
        try {
            var url = URL(asset.url)
            // GitHub redirects release downloads to its asset CDN. Reject cleartext/other hosts.
            for (attempt in 0..5) {
                check(url.protocol == "https" && url.userInfo == null && url.port == -1 &&
                    url.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"))
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "Edit-RiN-Update")
                currentCoroutineContext().ensureActive()
                val response = connection.responseCode
                if (response == 200) break
                check(response in listOf(301, 302, 303, 307, 308) && attempt < 5)
                val next = URL(url, requireNotNull(connection.getHeaderField("Location")))
                connection.disconnect()
                url = next
            }
            val active = requireNotNull(connection)
            check(active.responseCode == 200)
            val length = active.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            check(length <= MAX_UPDATE_BYTES && (length < 0 || length == asset.size))
            active.inputStream.use { input ->
                partial.outputStream().use { output -> copyUpdatePayload(input, output, asset, progress) }
            }
            currentCoroutineContext().ensureActive()
            validate(partial, release)
            check(partial.renameTo(ready))
            ready
        } finally {
            connection?.disconnect()
            partial.delete()
        }
    }

    fun validate(file: File, release: AppRelease) {
        val manager = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val candidate = requireNotNull(manager.getPackageArchiveInfo(file.absolutePath, flags))
        val current = manager.getPackageInfo(context.packageName, flags)
        check(eligibleUpdatePackage(
            UpdatePackageIdentity(candidate.packageName, candidate.versionName.orEmpty(), versionCode(candidate), signers(candidate)),
            UpdatePackageIdentity(current.packageName, current.versionName.orEmpty(), versionCode(current), signers(current)),
            release.version))
    }

    private fun signers(info: PackageInfo): Set<String> {
        val signatures = info.signingInfo?.apkContentsSigners
        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun versionCode(info: PackageInfo): Long = info.longVersionCode
}
