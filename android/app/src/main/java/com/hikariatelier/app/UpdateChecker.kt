package com.hikariatelier.app

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

internal const val RELEASES_URL = "https://github.com/rin-code-dev/Edit-RiN/releases"

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

internal const val MAX_UPDATE_BYTES = 128L * 1024 * 1024
internal data class ReleaseAsset(val url: String, val size: Long, val sha256: String? = null)
internal data class UpdateHighlight(val title: String, val description: String)
internal data class AppRelease(
    val tag: String,
    val version: ReleaseVersion,
    val apk: ReleaseAsset? = null,
    val highlights: List<UpdateHighlight> = emptyList(),
    val body: String = ""
)

internal fun extractLanguageSection(body: String, language: String): String {
    val normalized = language.lowercase(java.util.Locale.ROOT)
    val target = when {
        normalized.startsWith("ja") -> "ja"
        normalized.startsWith("zh") -> "zh"
        else -> "en"
    }
    val names = mapOf(
        "ja" to setOf("日本語", "japanese", "ja"),
        "zh" to setOf("中文", "简体中文", "簡體中文", "chinese", "zh"),
        "en" to setOf("english", "en")
    )
    val heading = Regex("""^ {0,3}(#{1,6})\s+(.+?)\s*#*\s*$""")
    fun section(wanted: String): String? {
        var level = 0
        val captured = mutableListOf<String>()
        var fence: Char? = null
        for (line in body.lines()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                val marker = trimmed.first()
                if (fence == null) fence = marker else if (fence == marker) fence = null
                if (level > 0) captured += line
                continue
            }
            val match = if (fence == null) heading.matchEntire(line) else null
            if (match != null) {
                val currentLevel = match.groupValues[1].length
                val label = match.groupValues[2].trim().lowercase(java.util.Locale.ROOT)
                if (level == 0 && label in names.getValue(wanted)) {
                    level = currentLevel
                    continue
                }
                if (level > 0 && currentLevel <= level) break
            }
            if (level > 0) captured += line
        }
        return captured.joinToString("\n").takeIf { it.isNotBlank() }
    }
    return section(target) ?: (if (target != "en") section("en") else null) ?: body
}

internal fun parseReleaseHighlights(body: String, language: String = "ja"): List<UpdateHighlight> {
    if (body.isBlank()) return emptyList()
    val section = extractLanguageSection(body, language)
    val lines = section.lines().map { it.trim() }
    val highlights = mutableListOf<UpdateHighlight>()
    var fence: Char? = null
    for (line in lines) {
        if (line.startsWith("```") || line.startsWith("~~~")) {
            val marker = line.first()
            if (fence == null) fence = marker else if (fence == marker) fence = null
            continue
        }
        if (fence != null || line.matches(Regex("""[-*_]{3,}"""))) continue
        val bullet = Regex("^[-*•\\d.]+\\s*(.*)$").matchEntire(line)?.groupValues?.get(1)?.trim() ?: continue
        if (bullet.isBlank()) continue
        val cleaned = bullet.replace("`", "").removeSurrounding("**").removeSurrounding("__").trim()
        val colonSplit = cleaned.split(Regex("[:：]\\s*"), limit = 2)
        val (rawTitle, rawDesc) = if (colonSplit.size == 2 && colonSplit[0].length <= 40) {
            colonSplit[0].trim().removeSurrounding("**") to colonSplit[1].trim().removeSurrounding("**")
        } else {
            val periodSplit = cleaned.split(Regex("[。.]\\s*"), limit = 2)
            if (periodSplit.size == 2 && periodSplit[0].length in 4..35) {
                periodSplit[0].trim().removeSurrounding("**") to periodSplit[1].trim().removeSurrounding("**")
            } else {
                val cut = cleaned.take(30).trim().removeSurrounding("**")
                val rest = cleaned.drop(cut.length).trim().removeSurrounding("**")
                cut to rest
            }
        }
        val title = rawTitle.take(45)
        val desc = rawDesc.take(120)
        if (title.isNotBlank()) {
            highlights.add(UpdateHighlight(title = title, description = desc))
        }
        if (highlights.size == 3) break
    }
    return highlights
}

internal fun releaseApk(release: org.json.JSONObject): ReleaseAsset? {
    val assets = release.optJSONArray("assets") ?: return null
    val tag = release.optString("tag_name")
    val expected = "Edit-RiN-$tag.apk"
    return (0 until assets.length()).mapNotNull { index ->
        val asset = assets.getJSONObject(index)
        if (asset.optString("name") != expected) return@mapNotNull null
        val url = asset.optString("browser_download_url")
        val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return@mapNotNull null
        if (parsed.scheme != "https" || parsed.host != "github.com" || parsed.userInfo != null ||
            parsed.port != -1 || parsed.path != "/rin-code-dev/Edit-RiN/releases/download/$tag/$expected" || parsed.query != null || parsed.fragment != null) return@mapNotNull null
        val size = asset.optLong("size")
        if (size !in 1..MAX_UPDATE_BYTES) return@mapNotNull null
        val digest = asset.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
        if (digest != null && !digest.matches(Regex("[0-9a-fA-F]{64}"))) return@mapNotNull null
        ReleaseAsset(url, size, digest?.lowercase())
    }.singleOrNull()
}

internal fun newestRelease(json: String): AppRelease? {
    val releases = JSONArray(json)
    return (0 until releases.length()).mapNotNull { index ->
        val release = releases.getJSONObject(index)
        if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) return@mapNotNull null
        val tag = release.optString("tag_name")
        val version = ReleaseVersion.parse(tag) ?: return@mapNotNull null
        if (version.suffix.isNotEmpty()) return@mapNotNull null
        val body = release.optString("body")
        val highlights = parseReleaseHighlights(body, "ja")
        AppRelease(tag, version, releaseApk(release), highlights, body)
    }.maxByOrNull { it.version }
}

// Run off the main thread at startup or on explicit request. No artwork or credentials are sent.
internal fun fetchNewestRelease(): AppRelease? {
    val connection = URL("https://api.github.com/repos/rin-code-dev/Edit-RiN/releases?per_page=100")
        .openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Edit-RiN-UpdateCheck")
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

internal data class UpdatePackageIdentity(val packageName: String, val versionName: String,
                                          val versionCode: Long, val signers: Set<String>)

internal fun eligibleUpdatePackage(candidate: UpdatePackageIdentity, installed: UpdatePackageIdentity,
                                   release: ReleaseVersion): Boolean =
    candidate.packageName == installed.packageName && candidate.versionCode > installed.versionCode &&
        ReleaseVersion.parse(candidate.versionName) == release &&
        candidate.signers.isNotEmpty() && candidate.signers == installed.signers
