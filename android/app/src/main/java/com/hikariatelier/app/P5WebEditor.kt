package com.hikariatelier.app

import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

internal const val P5_EDITOR_URL = "https://editor.p5js.org"
private const val MAX_ACCOUNT_RESPONSE_BYTES = 16 * 1024 * 1024

internal data class P5SketchFile(
    val path: String,
    val content: String,
    val url: String?
)

internal data class P5Sketch(
    val id: String,
    val name: String,
    val files: List<P5SketchFile>
)

internal data class ImportedP5Sketch(
    val name: String,
    val code: String,
    val supportingFiles: Map<String, String>,
    val assets: Map<String, ProjectAsset>,
    val p5Version: String,
    val p5SoundEnabled: Boolean
)

internal fun validP5Username(value: String): Boolean =
    value.length in 1..64 && value.all { it.isLetterOrDigit() || it in "._-" }

private data class P5Node(
    val id: String,
    val name: String,
    val content: String,
    val url: String?,
    val folder: Boolean,
    val children: List<String>
)

internal fun parseP5Sketches(json: String): List<P5Sketch> {
    val array = JSONObject(json).getJSONArray("projects")
    require(array.length() <= 500) { "Too many sketches" }
    return List(array.length()) { index ->
        val project = array.getJSONObject(index)
        val rawFiles = project.optJSONArray("files") ?: JSONArray()
        require(rawFiles.length() <= 500) { "Too many files" }
        val nodes = LinkedHashMap<String, P5Node>()
        repeat(rawFiles.length()) { fileIndex ->
            val file = rawFiles.getJSONObject(fileIndex)
            val id = file.optString("id").ifBlank { file.optString("_id") }
            if (id.isNotBlank()) {
                val children = file.optJSONArray("children")?.let { childArray ->
                    List(childArray.length()) { childArray.getString(it) }
                }.orEmpty()
                nodes[id] = P5Node(
                    id = id,
                    name = file.optString("name").take(200),
                    content = file.optString("content"),
                    url = file.optString("url").takeIf(String::isNotBlank),
                    folder = file.optString("fileType") == "folder",
                    children = children
                )
            }
        }

        val result = mutableListOf<P5SketchFile>()
        val visited = mutableSetOf<String>()
        fun visit(node: P5Node, parent: String) {
            if (!visited.add(node.id)) return
            val path = if (parent.isBlank()) node.name else "$parent/${node.name}"
            if (node.folder) {
                val nextParent = if (node.name == "root" && parent.isBlank()) "" else path
                node.children.mapNotNull(nodes::get).forEach { visit(it, nextParent) }
            } else if (path.isNotBlank()) {
                result += P5SketchFile(path, node.content, node.url)
            }
        }
        nodes.values.filter { it.folder && it.name == "root" }.forEach { visit(it, "") }
        nodes.values.filter { it.id !in visited }.forEach { visit(it, "") }

        P5Sketch(
            id = project.optString("id").ifBlank { project.getString("_id") },
            name = project.optString("name", "p5.js sketch").take(160),
            files = result
        )
    }
}

internal fun fetchP5Sketches(username: String): List<P5Sketch> {
    require(validP5Username(username)) { "Invalid username" }
    val encoded = Uri.encode(username)
    val connection = URL("$P5_EDITOR_URL/editor/$encoded/projects").openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Edit-KIRO/1.0.2")
        check(connection.responseCode == 200) { "Account request failed" }
        parseP5Sketches(String(readBounded(connection.inputStream, MAX_ACCOUNT_RESPONSE_BYTES.toLong()), Charsets.UTF_8))
    } finally {
        connection.disconnect()
    }
}

private fun readBounded(input: InputStream, limit: Long): ByteArray = input.use {
    val output = ByteArrayOutputStream()
    copyBounded(it, output, limit)
    output.toByteArray()
}

private fun openRemoteAsset(source: String): Pair<ByteArray, String?> {
    var url = URL(source)
    require(url.protocol == "https") { "Only HTTPS assets are supported" }
    repeat(4) { redirectCount ->
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 25_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Edit-KIRO/1.0.2")
            when (val status = connection.responseCode) {
                200 -> return readBounded(connection.inputStream, MAX_ASSET_BYTES) to connection.contentType
                301, 302, 303, 307, 308 -> {
                    check(redirectCount < 3) { "Too many redirects" }
                    url = URL(url, connection.getHeaderField("Location"))
                    require(url.protocol == "https") { "Unsafe redirect" }
                }
                else -> error("Asset request failed: $status")
            }
        } finally {
            connection.disconnect()
        }
    }
    error("Asset request failed")
}

private fun safeImportedName(path: String): String {
    val raw = path.substringAfterLast('/').ifBlank { "asset" }
    val safe = raw.map { character ->
        if (character.isISOControl() || character in "/\\?#%") '_' else character
    }.joinToString("").trim().trimStart('.').take(160)
    return safe.ifBlank { "asset" }
}

private fun replaceImportedPaths(source: String, replacements: Map<String, String>): String {
    var result = source
    replacements.entries.sortedByDescending { it.key.length }.forEach { (original, replacement) ->
        result = result.replace("./$original", replacement).replace(original, replacement)
    }
    return result
}

internal fun importP5Sketch(sketch: P5Sketch, storage: AssetStorage): ImportedP5Sketch {
    val indexHtml = sketch.files.firstOrNull {
        it.path.substringAfterLast('/').equals("index.html", true)
    }?.content.orEmpty()
    val p5Version = if (
        Regex("""(?:p5@|/p5/|p5-)(2)(?:\.|/)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(indexHtml)
    ) P5_VERSION_CURRENT else P5_VERSION_LEGACY
    val p5SoundEnabled = indexHtml.contains("p5.sound", ignoreCase = true)
    val assetFiles = sketch.files.filter { file ->
        val extension = file.path.substringAfterLast('.', "").lowercase()
        extension !in setOf("js", "html", "htm", "css") && (file.content.isNotEmpty() || file.url != null)
    }
    require(assetFiles.size <= 100) { "Too many assets" }

    val names = mutableSetOf<String>()
    val replacements = LinkedHashMap<String, String>()
    val assets = LinkedHashMap<String, ProjectAsset>()
    assetFiles.forEach { file ->
        val name = uniqueAssetName(safeImportedName(file.path), names)
        names += name
        val (bytes, providedMime) = file.url?.let(::openRemoteAsset)
            ?: (file.content.toByteArray(Charsets.UTF_8) to null)
        val asset = storage.put(ByteArrayInputStream(bytes), assetMimeType(name, providedMime))
        assets[name] = asset
        replacements[file.path] = "assets/$name"
    }
    validateAssetSet(assets)

    val javaScript = sketch.files.filter { it.path.substringAfterLast('.', "").equals("js", true) }
    val main = javaScript.firstOrNull { it.path == "sketch.js" }
        ?: javaScript.firstOrNull { it.path.endsWith("/sketch.js") }
        ?: javaScript.firstOrNull()
        ?: error("sketch.js is missing")
    val supportNames = mutableSetOf<String>()
    val supporting = LinkedHashMap<String, String>()
    javaScript.filterNot { it === main }.forEach { file ->
        val base = file.path.substringAfterLast('/').ifBlank { "library.js" }
        var name = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (!name.endsWith(".js", true)) name += ".js"
        name = generateSequence(name to 1) { (candidate, number) ->
            val stem = candidate.removeSuffix(".js").substringBeforeLast("-$number", candidate.removeSuffix(".js"))
            "$stem-${number + 1}.js" to number + 1
        }.map { it.first }.first { it != "sketch.js" && supportNames.add(it) }
        supporting[name] = replaceImportedPaths(file.content, replacements)
    }

    return ImportedP5Sketch(
        name = sketch.name.ifBlank { "p5.js sketch" },
        code = replaceImportedPaths(main.content, replacements),
        supportingFiles = supporting,
        assets = assets,
        p5Version = p5Version,
        p5SoundEnabled = p5SoundEnabled
    )
}
