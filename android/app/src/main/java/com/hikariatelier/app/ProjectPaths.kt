package com.hikariatelier.app

import java.net.URI

internal const val MAX_PROJECT_TEXT_BYTES = 16L * 1024 * 1024
internal const val MAX_PROJECT_TEXT_FILES = 500
internal const val PROJECT_RUNTIME_DIRECTORY = "__edit-rin__"

/** URLs remain rooted in one project; filenames never become filesystem paths. */
internal fun validProjectPath(path: String): Boolean = path.isNotBlank() && path == path.trim() &&
    path.length <= 1024 && path.none { it in "\\?#%:" || it.isISOControl() } &&
    path.split('/').all { it.isNotEmpty() && it !in setOf(".", "..", PROJECT_RUNTIME_DIRECTORY) && it.length <= 200 }

internal fun isJavaScriptProjectFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf("js", "mjs", "cjs")
internal fun isClassicJavaScriptProjectFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf("js", "cjs")
internal fun isHtmlProjectFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf("html", "htm")
internal fun isProjectTextFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf(
    "js", "mjs", "cjs", "html", "htm", "css", "json", "map", "txt", "md", "csv", "tsv", "xml", "svg",
    "vert", "frag", "glsl", "wgsl", "obj", "mtl", "yaml", "yml", "toml", "ini")

internal fun projectTextMimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "js", "mjs", "cjs" -> "application/javascript"
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "json", "map" -> "application/json"
    "xml" -> "application/xml"
    "svg" -> "image/svg+xml"
    else -> "text/plain"
}

internal fun validateProjectTextFiles(files: Map<String, String>, main: String = "") {
    require(files.size <= MAX_PROJECT_TEXT_FILES) { "A project supports up to 500 text files" }
    require(files.keys.all(::validProjectPath)) { "Use a relative project path without traversal or URL escapes" }
    require(files.values.fold(main.toByteArray(Charsets.UTF_8).size.toLong()) { size, value ->
        size + value.toByteArray(Charsets.UTF_8).size
    } <= MAX_PROJECT_TEXT_BYTES) { "Project text exceeds the 16 MiB limit" }
}

internal fun normalizedExternalScriptUrl(value: String): String? = runCatching {
    val trimmed = value.trim()
    val uri = URI(trimmed)
    trimmed.takeIf { it.length <= 2048 && uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null && uri.rawFragment == null && it.none(Char::isISOControl) }
}.getOrNull()

/** Encoded slash/backslash and double encoding cannot smuggle a new path segment. */
internal fun projectPathFromUrl(url: String, token: String): String? = runCatching {
    val uri = URI(url)
    if (uri.scheme != "https" || uri.host != "appassets.androidplatform.net" || uri.rawUserInfo != null ||
        Regex("%2f|%5c", RegexOption.IGNORE_CASE).containsMatchIn(uri.rawPath.orEmpty())) return null
    val prefix = "/project/$token/"
    val path = uri.path ?: return null
    path.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.takeIf(::validProjectPath)
}.getOrNull()

internal fun projectFileUrl(token: String, path: String): String {
    require(validProjectPath(path))
    val encoded = URI(null, null, "/project/$token/$path", null).toASCIIString()
    return PREVIEW_ORIGIN + encoded
}

internal fun projectRuntimeAssetUrl(token: String, name: String): String {
    require(name in setOf("p5_host.js", "p5_bootstrap.js", "p5_sketch.js"))
    return "$PREVIEW_ORIGIN/project/$token/$PROJECT_RUNTIME_DIRECTORY/$name"
}

internal fun previewProjectSourceLocation(assets: PreviewAssets, sourceUrl: String, line: Int): PreviewSourceLocation? {
    if (line <= 0) return null
    val path = projectPathFromUrl(sourceUrl, assets.token) ?: return null
    return path.takeIf { it in assets.virtualFiles }?.let { PreviewSourceLocation(it, line) }
}
