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
                require(id !in nodes) { "Duplicate project file IDs" }
                val children = file.optJSONArray("children")?.let { childArray ->
                    List(childArray.length()) { childArray.getString(it) }
                }.orEmpty()
                nodes[id] = P5Node(
                    id = id,
                    name = file.optString("name"),
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

        require(result.map { it.path }.distinct().size == result.size) { "Duplicate project file paths" }
        require(result.all { validProjectPath(it.path) }) { "Invalid relative project path" }
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
        connection.setRequestProperty("User-Agent", "Edit-RiN/1.0.3")
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

private fun importRemoteAsset(source: String, name: String, storage: AssetStorage): ProjectAsset {
    var url = URL(source)
    require(url.protocol == "https") { "Only HTTPS assets are supported" }
    repeat(4) { redirectCount ->
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 25_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Edit-RiN/1.0.3")
            when (val status = connection.responseCode) {
                200 -> return connection.inputStream.use { input ->
                    storage.put(input, assetMimeType(name, connection.contentType))
                }
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

private fun importRemoteText(source: String): String {
    var url = URL(source)
    require(url.protocol == "https") { "Only HTTPS text files are supported" }
    repeat(4) { redirectCount ->
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000; connection.readTimeout = 25_000
            connection.instanceFollowRedirects = false
            when (val status = connection.responseCode) {
                200 -> return String(readBounded(connection.inputStream, MAX_PROJECT_TEXT_BYTES), Charsets.UTF_8)
                301, 302, 303, 307, 308 -> {
                    check(redirectCount < 3) { "Too many redirects" }
                    url = URL(url, connection.getHeaderField("Location"))
                    require(url.protocol == "https") { "Unsafe redirect" }
                }
                else -> error("Text request failed: $status")
            }
        } finally { connection.disconnect() }
    }
    error("Text request failed")
}

private val binaryProjectExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "avif", "mp3", "wav",
    "ogg", "m4a", "mp4", "webm", "mov", "ttf", "otf", "woff", "woff2", "wasm", "pdf", "zip", "bin")

internal fun importP5Sketch(sketch: P5Sketch, storage: AssetStorage): ImportedP5Sketch {
    require(sketch.files.size <= MAX_PROJECT_TEXT_FILES) { "A project supports up to 500 files" }
    require(sketch.files.all { validProjectPath(it.path) }) { "Invalid relative project path" }
    require(sketch.files.map { it.path }.distinct().size == sketch.files.size) { "Duplicate project file paths" }
    val textFiles = sketch.files.filter { file ->
        val extension = file.path.substringAfterLast('.', "").lowercase()
        isProjectTextFile(file.path) || extension !in binaryProjectExtensions && file.url == null
    }
    val assetFiles = sketch.files.filterNot { it in textFiles }
    require(assetFiles.size <= 100) { "A project supports up to 100 binary assets" }
    val supporting = linkedMapOf<String, String>()
    var textBytes = 0L
    textFiles.forEach { file ->
        val content = if (file.content.isEmpty() && file.url != null) importRemoteText(file.url) else file.content
        textBytes += content.toByteArray(Charsets.UTF_8).size
        require(textBytes <= MAX_PROJECT_TEXT_BYTES) { "Project text exceeds the 16 MiB limit" }
        supporting[file.path] = content
    }
    val main = supporting.remove("sketch.js").orEmpty()
    val config = readProjectDocumentConfig(supporting)
    val indexHtml = projectEntryDocument(supporting, config)?.let(supporting::get).orEmpty()
    require(main.isNotEmpty() || supporting.keys.any { isJavaScriptProjectFile(it) || isHtmlProjectFile(it) }) { "Project has no HTML or JavaScript entry" }
    val p5Version = if (Regex("""(?:p5@|/(?:p5|p5\.js)/|p5-)(2)(?:\.|/)""", RegexOption.IGNORE_CASE)
        .containsMatchIn(indexHtml)) P5_VERSION_CURRENT else P5_VERSION_LEGACY
    validateProjectTextFiles(supporting, main)
    val stagingDirectory = storage.stagingDirectory()
    val staged = AssetStorage(stagingDirectory)
    try {
        val assets = linkedMapOf<String, ProjectAsset>()
        assetFiles.forEach { file ->
            assets[file.path] = if (file.url != null) importRemoteAsset(file.url, file.path, staged)
                else ByteArrayInputStream(file.content.toByteArray(Charsets.UTF_8)).use { staged.put(it, assetMimeType(file.path, null)) }
            validateAssetSet(assets)
        }
        storage.commitStaged(staged, assets.values)
        return ImportedP5Sketch(sketch.name.ifBlank { "p5.js sketch" }, main, supporting, assets, p5Version,
            indexHtml.contains("p5.sound", ignoreCase = true))
    } finally { stagingDirectory.deleteRecursively() }
}
