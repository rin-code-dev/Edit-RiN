package com.hikariatelier.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal data class AssetRenameCandidate(
    val file: String,
    val line: Int,
    val start: Int,
    val end: Int,
    val original: String,
    val excerpt: String
)

internal data class AssetRenameRequest(
    val oldName: String,
    val newName: String,
    val selectedCandidates: List<AssetRenameCandidate> = emptyList()
)

private data class AssetPathLiteral(val start: Int, val end: Int)

/** Only complete, unescaped literal paths are offered. No evaluation or global replacement. */
internal fun assetRenameCandidates(sources: Map<String, String>, oldName: String): List<AssetRenameCandidate> {
    require(validAssetName(oldName))
    return buildList {
        sources.forEach { (file, code) ->
            val extension = file.substringAfterLast('.', "").lowercase()
            val literals = when {
                isJavaScriptProjectFile(file) || extension in setOf("json", "map") -> codePathLiterals(code)
                isHtmlProjectFile(file) -> htmlPathLiterals(code)
                extension == "css" -> cssPathLiterals(code)
                else -> emptyList()
            }
            var line = 1
            var scanned = 0
            literals.forEach literalLoop@ { literal ->
                val path = code.substring(literal.start, literal.end)
                if (path != "assets/$oldName" && path != "./assets/$oldName") return@literalLoop
                val lineStart = code.lastIndexOf('\n', literal.start - 1) + 1
                val lineEnd = code.indexOf('\n', literal.end).let { if (it < 0) code.length else it }
                val excerptStart = maxOf(lineStart, literal.start - 60)
                val excerptEnd = minOf(lineEnd, literal.end + 80)
                while (scanned < literal.start) {
                    if (code[scanned] == '\n') line++
                    scanned++
                }
                add(AssetRenameCandidate(file, line,
                    literal.start, literal.end, path,
                    (if (excerptStart > lineStart) "…" else "") + code.substring(excerptStart, excerptEnd).trimEnd('\r') +
                        (if (excerptEnd < lineEnd) "…" else "")))
            }
        }
    }
}

/** Validate all selections before producing any edits. Persist this result with the asset rename. */
internal fun applyAssetRename(sources: Map<String, String>, request: AssetRenameRequest): Map<String, String> {
    require(validAssetName(request.oldName) && validAssetName(request.newName))
    if (request.selectedCandidates.isEmpty()) return sources.toMap()
    val eligible = assetRenameCandidates(sources, request.oldName).associateBy { Triple(it.file, it.start, it.end) }
    val selectedKeys = request.selectedCandidates.map { Triple(it.file, it.start, it.end) }
    require(selectedKeys.distinct().size == selectedKeys.size) { "Duplicate asset references" }
    request.selectedCandidates.forEach { candidate ->
        val current = eligible[Triple(candidate.file, candidate.start, candidate.end)]
        require(current != null && current.original == candidate.original) { "Asset references changed; reopen rename" }
    }
    val byFile = request.selectedCandidates.groupBy { it.file }
    byFile.values.forEach { candidates ->
        candidates.sortedBy { it.start }.zipWithNext().forEach { (a, b) ->
            require(a.end <= b.start) { "Overlapping asset references" }
        }
    }
    return sources.mapValues { (file, original) ->
        var updated = original
        byFile[file].orEmpty().sortedByDescending { it.start }.forEach { candidate ->
            val encoded = encodedAssetReplacement(original, file, candidate, request.newName)
            updated = updated.replaceRange(candidate.start, candidate.end, encoded)
        }
        updated
    }
}

/** Publish after persistence succeeds, as one editor action so Undo retains the previous value. */
internal fun assetRenameEditorValue(before: TextFieldValue, file: String, request: AssetRenameRequest): TextFieldValue {
    val selected = request.selectedCandidates.filter { it.file == file }
    if (selected.isEmpty()) return before
    val localRequest = request.copy(selectedCandidates = selected)
    val updated = applyAssetRename(mapOf(file to before.text), localRequest).getValue(file)
    fun adjusted(offset: Int): Int {
        var delta = 0
        selected.sortedBy { it.start }.forEach { candidate ->
            val replacementSize = encodedAssetReplacement(before.text, file, candidate, request.newName).length
            if (offset < candidate.start) return offset + delta
            if (offset < candidate.end) return candidate.start + delta + minOf(offset - candidate.start, replacementSize)
            delta += replacementSize - (candidate.end - candidate.start)
        }
        return offset + delta
    }
    return TextFieldValue(updated, TextRange(adjusted(before.selection.start), adjusted(before.selection.end)))
}

private fun encodedAssetReplacement(code: String, file: String, candidate: AssetRenameCandidate, newName: String): String {
    val path = (if (candidate.original.startsWith("./")) "./" else "") + "assets/$newName"
    val quote = code[candidate.start - 1]
    return if (isHtmlProjectFile(file)) {
        path.replace("&", "&amp;").replace("\"", "&quot;").replace("'", "&#39;")
    } else {
        path.replace("\\", "\\\\").replace(quote.toString(), "\\$quote")
            .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    }
}

private fun codePathLiterals(code: String): List<AssetPathLiteral> = buildList {
    var index = 0
    var previousToken = ""
    while (index < code.length) {
        val c = code[index]
        when {
            c.isWhitespace() -> index++
            code.startsWith("//", index) -> index = code.indexOf('\n', index + 2).let { if (it < 0) code.length else it }
            code.startsWith("/*", index) -> index = skipBlock(code, index, "*/")
            c == '`' -> { index = skipTemplate(code, index); previousToken = "literal" }
            c == '\'' || c == '"' -> {
                val next = skipQuoted(code, index)
                val contentEnd = next - 1
                val complete = next <= code.length && contentEnd > index && code.getOrNull(contentEnd) == c
                val raw = if (complete) code.substring(index + 1, contentEnd) else ""
                val following = nextCodeToken(code, next)
                if (complete && '\\' !in raw && '\n' !in raw && '\r' !in raw &&
                    previousToken != "+" && following !in setOf("+", ".", "[")) {
                    add(AssetPathLiteral(index + 1, contentEnd))
                }
                index = next; previousToken = "literal"
            }
            c == '/' && previousToken in setOf("", "=", "(", ",", ":", "[", "{", ";", "!", "?", "return", "throw", "case", "yield", "=>") -> {
                index = skipRegularExpression(code, index); previousToken = "literal"
            }
            c.isLetterOrDigit() || c == '_' || c == '$' -> {
                val begin = index++
                while (index < code.length && (code[index].isLetterOrDigit() || code[index] in "_$")) index++
                previousToken = code.substring(begin, index)
            }
            else -> {
                previousToken = if (code.startsWith("=>", index)) "=>" else c.toString()
                index += previousToken.length
            }
        }
    }
}

private fun nextCodeToken(code: String, from: Int): String {
    var index = from
    while (index < code.length) {
        when {
            code[index].isWhitespace() -> index++
            code.startsWith("//", index) -> index = code.indexOf('\n', index + 2).let { if (it < 0) code.length else it }
            code.startsWith("/*", index) -> index = skipBlock(code, index, "*/")
            else -> return code[index].toString()
        }
    }
    return ""
}

private fun skipBlock(code: String, start: Int, closing: String): Int =
    code.indexOf(closing, start + 2).let { if (it < 0) code.length else it + closing.length }

private fun skipQuoted(code: String, start: Int): Int {
    val quote = code[start]
    var index = start + 1
    while (index < code.length) {
        when (code[index]) {
            '\\' -> index = (index + 2).coerceAtMost(code.length)
            quote -> return index + 1
            else -> index++
        }
    }
    return code.length
}

private fun skipTemplate(code: String, start: Int, depth: Int = 0): Int {
    if (depth > 32) return code.length
    var index = start + 1
    while (index < code.length) {
        when {
            code[index] == '\\' -> index = (index + 2).coerceAtMost(code.length)
            code[index] == '`' -> return index + 1
            code.startsWith("${'$'}{", index) -> index = skipTemplateExpression(code, index + 2, depth + 1)
            else -> index++
        }
    }
    return index
}

private fun skipTemplateExpression(code: String, start: Int, depth: Int): Int {
    var braces = 1
    var index = start
    while (index < code.length) {
        when {
            code.startsWith("//", index) -> index = code.indexOf('\n', index + 2).let { if (it < 0) code.length else it }
            code.startsWith("/*", index) -> index = skipBlock(code, index, "*/")
            code[index] == '\'' || code[index] == '"' -> index = skipQuoted(code, index)
            code[index] == '`' -> index = skipTemplate(code, index, depth)
            code[index] == '/' -> index = skipRegularExpression(code, index)
            code[index] == '{' -> { braces++; index++ }
            code[index] == '}' -> { braces--; index++; if (braces == 0) return index }
            else -> index++
        }
    }
    return index
}

private fun skipRegularExpression(code: String, start: Int): Int {
    var index = start + 1
    var characterClass = false
    while (index < code.length) {
        when (code[index]) {
            '\\' -> index = (index + 2).coerceAtMost(code.length)
            '[' -> { characterClass = true; index++ }
            ']' -> { characterClass = false; index++ }
            '/' -> if (!characterClass) return index + 1 else index++
            '\n', '\r' -> return index
            else -> index++
        }
    }
    return index
}

private fun htmlPathLiterals(code: String): List<AssetPathLiteral> = buildList {
    var index = 0
    while (index < code.length) {
        when {
            code.startsWith("<!--", index) -> index = code.indexOf("-->", index + 4).let { if (it < 0) code.length else it + 3 }
            code[index] == '<' -> {
                val tagEnd = code.indexOf('>', index + 1).let { if (it < 0) code.length else it }
                val tagName = Regex("^<\\s*([A-Za-z][A-Za-z0-9:-]*)").find(code.substring(index, tagEnd))
                    ?.groupValues?.get(1)?.lowercase()
                if (tagName == null) { index++; continue }
                var cursor = index + 1
                while (cursor < tagEnd) {
                    if (code[cursor] == '\'' || code[cursor] == '"') {
                        val quote = code[cursor]
                        val end = code.indexOf(quote, cursor + 1)
                        if (end < 0 || end > tagEnd) break
                        val attribute = Regex("([A-Za-z_:][A-Za-z0-9_:.-]*)\\s*=\\s*$")
                            .find(code.substring(index, cursor))?.groupValues?.get(1)?.lowercase()
                        if (attribute in setOf("src", "href", "poster", "data-src", "xlink:href"))
                            add(AssetPathLiteral(cursor + 1, end))
                        cursor = end + 1
                    } else cursor++
                }
                index = (tagEnd + 1).coerceAtMost(code.length)
                if (tagName in setOf("script", "style")) {
                    val closing = code.indexOf("</$tagName", index, ignoreCase = true)
                    index = if (closing < 0) code.length else closing
                }
            }
            else -> index++
        }
    }
}

private fun cssPathLiterals(code: String): List<AssetPathLiteral> = buildList {
    var index = 0
    while (index < code.length) {
        when {
            code.startsWith("/*", index) -> index = skipBlock(code, index, "*/")
            code[index] == '\'' || code[index] == '"' -> {
                val end = skipQuoted(code, index) - 1
                var prefixEnd = index
                while (prefixEnd > 0 && code[prefixEnd - 1].isWhitespace()) prefixEnd--
                val isUrl = prefixEnd >= 4 && code.substring(prefixEnd - 4, prefixEnd).equals("url(", ignoreCase = true)
                if (end > index && code.getOrNull(end) == code[index] && isUrl &&
                    '\\' !in code.substring(index + 1, end)) add(AssetPathLiteral(index + 1, end))
                index = (end + 1).coerceAtLeast(index + 1)
            }
            else -> index++
        }
    }
}
