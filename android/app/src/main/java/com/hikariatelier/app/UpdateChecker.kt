package com.hikariatelier.app

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

internal const val RELEASES_URL = "https://github.com/rin-code-dev/EDIT-KIRO/releases"

internal data class ReleaseVersion(val numbers: List<Long>, val suffix: List<String>) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        numbers.zip(other.numbers).forEach { (a, b) -> if (a != b) return a.compareTo(b) }
        if (suffix.isEmpty() != other.suffix.isEmpty()) return if (suffix.isEmpty()) 1 else -1
        suffix.zip(other.suffix).forEach { (a, b) ->
            val x = a.toLongOrNull()
            val y = b.toLongOrNull()
            val comparison = when {
                x != null && y != null -> x.compareTo(y)
                x != null -> -1
                y != null -> 1
                else -> a.compareTo(b)
            }
            if (comparison != 0) return comparison
        }
        return suffix.size.compareTo(other.suffix.size)
    }

    companion object {
        fun parse(tag: String): ReleaseVersion? {
            val match = Regex("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
                .matchEntire(tag) ?: return null
            val numbers = (1..3).map {
                if (it == 3 && match.groupValues[it].isEmpty()) 0L
                else match.groupValues[it].toLongOrNull() ?: return null
            }
            val suffix = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            if (suffix.any { it.isEmpty() }) return null
            return ReleaseVersion(numbers, suffix)
        }
    }
}

internal data class AppRelease(val tag: String, val version: ReleaseVersion)

internal fun newestRelease(json: String): AppRelease? {
    val releases = JSONArray(json)
    return (0 until releases.length()).mapNotNull { index ->
        val release = releases.getJSONObject(index)
        if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) return@mapNotNull null
        val tag = release.optString("tag_name")
        val version = ReleaseVersion.parse(tag) ?: return@mapNotNull null
        if (version.suffix.isNotEmpty()) return@mapNotNull null
        AppRelease(tag, version)
    }.maxByOrNull { it.version }
}

// Run off the main thread at startup or on explicit request. No artwork or credentials are sent.
internal fun fetchNewestRelease(): AppRelease? {
    val connection = URL("https://api.github.com/repos/rin-code-dev/EDIT-KIRO/releases?per_page=100")
        .openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Edit-KIRO-UpdateCheck")
        check(connection.responseCode == 200) { "Update check failed" }
        val bytes = connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= 4 * 1024 * 1024) { "Response too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        newestRelease(String(bytes, Charsets.UTF_8))
    } finally {
        connection.disconnect()
    }
}

internal data class UpdateResult(val message: String?, val release: AppRelease? = null)

internal fun evaluateUpdate(release: AppRelease?, currentName: String, silent: Boolean): UpdateResult {
    val current = ReleaseVersion.parse(currentName)
    if (release != null && current != null && release.version > current) {
        return UpdateResult("新しいバージョンがあります", release)
    }
    return UpdateResult(if (silent) null else when {
        current == null -> "バージョンを比較できませんでした"
        release == null -> "公開済みのバージョンが見つかりません"
        else -> "新しいアップデートはありません"
    })
}

internal fun updateFailureMessage(silent: Boolean): String? = if (silent) null else
    "確認できませんでした。通信環境を確認して、もう一度お試しください"
