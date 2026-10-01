package com.hikariatelier.app

internal data class PreviewSourceFile(val file: String, val startLine: Int, val lineCount: Int)
internal data class PreviewSourceLocation(val file: String, val line: Int)

// Keep the separator line count in sync with composeProjectSource.
internal fun previewSourceFiles(main: String, files: Map<String, String>): List<PreviewSourceFile> {
    var start = 1
    return buildList {
        files.filter { it.key.endsWith(".js", ignoreCase = true) }.toSortedMap().forEach { (name, code) ->
            val count = code.count { it == '\n' } + 1
            add(PreviewSourceFile(name, start, count))
            start += count - 1 + PREVIEW_FILE_SEPARATOR.count { it == '\n' }
        }
        add(PreviewSourceFile("sketch.js", start, main.count { it == '\n' } + 1))
    }
}

internal fun previewSourceLocation(files: List<PreviewSourceFile>, line: Int): PreviewSourceLocation? =
    files.firstOrNull { line >= it.startLine && line < it.startLine + it.lineCount }
        ?.let { PreviewSourceLocation(it.file, line - it.startLine + 1) }

/** Console callbacks carry their original script URL even after WebView navigation. */
internal fun previewTokenFromSource(source: String): String? {
    val query = source.substringAfter("sketch.js?run=", "")
    if (query.isNotEmpty()) return query.substringBefore('&')
    if (!source.startsWith("$PREVIEW_ORIGIN/project/")) return null
    return source.removePrefix("$PREVIEW_ORIGIN/project/").substringBefore('/').takeIf { it.isNotEmpty() }
}

internal fun sourceLineOffset(text: String, line: Int): Int? {
    if (line <= 0) return null
    var offset = 0
    repeat(line - 1) {
        val newline = text.indexOf('\n', offset)
        if (newline < 0) return null
        offset = newline + 1
    }
    return offset
}
