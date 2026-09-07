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
            val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
                .matchEntire(tag) ?: return null
            val numbers = (1..3).map { match.groupValues[it].toLongOrNull() ?: return null }
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
        if (release.optBoolean("draft", false)) return@mapNotNull null
        val tag = release.optString("tag_name")
        val version = ReleaseVersion.parse(tag) ?: return@mapNotNull null
        AppRelease(tag, version)
    }.maxByOrNull { it.version }
}

// Call only on user request, off the main thread. No credentials or artwork are sent.
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
