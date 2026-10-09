package com.hikariatelier.app

import android.content.res.AssetManager
import org.json.JSONObject
import java.security.MessageDigest

/** Actual canvas captures, validated against the bundled source; never used for user-owned copies. */
internal class BundledThumbnailCatalog(private val assets: AssetManager) {
    private val manifest by lazy {
        runCatching { JSONObject(assets.open("public/sample-previews/manifest.json").bufferedReader().use { it.readText() }) }
            .getOrNull()
    }
    fun assetPath(work: Work, input: PreviewRunInput): String? {
        if (!work.isSample || input.files.isNotEmpty() || input.assets.isNotEmpty() ||
            input.libraries.isNotEmpty() || input.parameterValues.isNotEmpty()) return null
        val id = work.id.removePrefix("builtin-sample:").trimEnd(':')
        val entry = manifest?.optJSONObject(id) ?: return null
        val source = runCatching { assets.open("public/samples/${entry.getString("source")}").bufferedReader().use { it.readText() } }
            .getOrNull() ?: return null
        val hash = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
        if (hash != entry.optString("sha256") || entry.optString("p5Version") != input.p5Version || input.soundEnabled) return null
        if (listOf("en", "ja", "zh").none { input.source == localizedSampleCode(source, it) }) return null
        return "public/sample-previews/$id.png"
    }
}
