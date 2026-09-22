package com.hikariatelier.app

internal data class PreviewSourceFile(val file: String, val startLine: Int, val lineCount: Int)
internal data class PreviewSourceLocation(val file: String, val line: Int)

// Keep the two separator lines in sync with composeProjectSource.
internal fun previewSourceFiles(main: String, files: Map<String, String>): List<PreviewSourceFile> {
    var start = 1
    return buildList {
        files.toSortedMap().forEach { (name, code) ->
            val count = code.count { it == '\n' } + 1
            add(PreviewSourceFile(name, start, count))
            start += count + 1
        }
        add(PreviewSourceFile("sketch.js", start, main.count { it == '\n' } + 1))
    }
}

internal fun previewSourceLocation(files: List<PreviewSourceFile>, line: Int): PreviewSourceLocation? =
    files.firstOrNull { line >= it.startLine && line < it.startLine + it.lineCount }
        ?.let { PreviewSourceLocation(it.file, line - it.startLine + 1) }

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
