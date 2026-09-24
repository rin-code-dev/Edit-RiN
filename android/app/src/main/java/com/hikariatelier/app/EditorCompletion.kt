package com.hikariatelier.app

import androidx.compose.ui.text.input.TextFieldValue

private val P5_COMPLETIONS = listOf(
    "setup", "draw", "createCanvas", "resizeCanvas", "windowWidth", "windowHeight",
    "background", "fill", "stroke", "strokeWeight", "noStroke", "noFill", "colorMode",
    "circle", "ellipse", "rect", "line", "triangle", "beginShape", "vertex", "endShape",
    "push", "pop", "translate", "rotate", "scale", "text", "textSize", "image", "loadImage",
    "random", "noise", "map", "dist", "lerp", "constrain", "floor", "ceil", "round", "abs",
    "sin", "cos", "tan", "mouseX", "mouseY", "frameCount", "deltaTime", "millis",
    "mousePressed", "mouseDragged", "mouseReleased", "touchStarted", "touchMoved", "touchEnded"
)
private val DECLARATION_WORDS = setOf("function", "class", "const", "let", "var")
private val REGEX_PREFIXES = setOf("=", "(", "[", "{", ",", ":", ";", "return", "case", "!", "?")

internal fun completionPrefix(value: TextFieldValue): String {
    if (!value.selection.collapsed) return ""
    val end = value.selection.start.coerceIn(0, value.text.length)
    var start = end
    while (start > 0 && (value.text[start - 1].isLetterOrDigit() || value.text[start - 1] == '_' ||
        value.text[start - 1] == '$')) start--
    return value.text.substring(start, end)
}

internal fun editorCompletions(value: TextFieldValue): List<String> {
    val prefix = completionPrefix(value)
    if (prefix.length < 2) return emptyList()
    return P5_COMPLETIONS.asSequence()
        .filter { it.startsWith(prefix, ignoreCase = true) && it != prefix }
        .take(8).toList()
}

internal data class ProjectSymbol(val name: String, val file: String)
internal data class CompletionCandidate(val name: String, val file: String? = null)

/** Collect declarations without treating words in comments and literals as code. */
internal fun projectSymbols(sources: Map<String, String>): List<ProjectSymbol> = buildList {
    val seen = HashSet<Pair<String, String>>()
    for ((file, source) in sources) {
        var index = 0
        var expectedName = false
        var previousToken = ""
        while (index < source.length) {
            val character = source[index]
            val next = source.getOrNull(index + 1)
            when {
                character.isWhitespace() -> index++
                character == '/' && next == '/' -> {
                    index += 2
                    while (index < source.length && source[index] != '\n') index++
                }
                character == '/' && next == '*' -> {
                    val end = source.indexOf("*/", index + 2)
                    index = if (end < 0) source.length else end + 2
                }
                character == '/' && previousToken in REGEX_PREFIXES -> {
                    val end = regexLiteralEnd(source, index)
                    index = end ?: index + 1
                    expectedName = false
                    previousToken = if (end == null) "/" else "literal"
                }
                character == '\'' || character == '"' || character == '`' -> {
                    val quote = character
                    index++
                    while (index < source.length) {
                        val current = source[index++]
                        if (current == '\\') index = (index + 1).coerceAtMost(source.length)
                        else if (current == quote) break
                    }
                    expectedName = false
                    previousToken = "literal"
                }
                character.isLetter() || character == '_' || character == '$' -> {
                    val start = index++
                    while (index < source.length &&
                        (source[index].isLetterOrDigit() || source[index] == '_' || source[index] == '$')) index++
                    val word = source.substring(start, index)
                    if (expectedName) {
                        if (seen.add(file to word)) add(ProjectSymbol(word, file))
                        expectedName = false
                    } else {
                        expectedName = previousToken != "." && word in DECLARATION_WORDS
                    }
                    previousToken = word
                }
                else -> {
                    if (character != '*' || previousToken != "function") expectedName = false
                    previousToken = character.toString()
                    index++
                }
            }
        }
    }
}

private fun regexLiteralEnd(source: String, start: Int): Int? {
    var index = start + 1
    var inCharacterClass = false
    while (index < source.length && source[index] != '\n' && source[index] != '\r') {
        when (source[index++]) {
            '\\' -> index = (index + 1).coerceAtMost(source.length)
            '[' -> inCharacterClass = true
            ']' -> inCharacterClass = false
            '/' -> if (!inCharacterClass) {
                while (index < source.length && source[index].isLetter()) index++
                return index
            }
        }
    }
    return null
}

internal fun projectCompletions(
    value: TextFieldValue,
    symbols: List<ProjectSymbol>,
    currentFile: String
): List<CompletionCandidate> {
    val prefix = completionPrefix(value)
    if (prefix.length < 2) return emptyList()
    val result = ArrayList<CompletionCandidate>(8)
    val seen = HashSet<String>()
    for (symbol in symbols.asSequence().filter { it.file == currentFile } +
        symbols.asSequence().filter { it.file != currentFile }) {
        if (symbol.name.startsWith(prefix, ignoreCase = true) && symbol.name != prefix &&
            seen.add(symbol.name)) result.add(CompletionCandidate(symbol.name, symbol.file))
        if (result.size == 8) return result
    }
    for (name in P5_COMPLETIONS) {
        if (name.startsWith(prefix, ignoreCase = true) && name != prefix && seen.add(name)) {
            result.add(CompletionCandidate(name))
        }
        if (result.size == 8) break
    }
    return result
}

internal fun completionHelp(name: String, text: (String) -> String): String {
    val detail = when (name) {
        "circle" -> "(x, y, d)" to "円を描画"
        "rect" -> "(x, y, w, h)" to "四角形を描画"
        "ellipse" -> "(x, y, w, h)" to "楕円を描画"
        "line" -> "(x1, y1, x2, y2)" to "線を描画"
        "createCanvas", "resizeCanvas" -> "(width, height)" to "キャンバスの大きさ"
        "background", "fill", "stroke" -> "(color)" to "色を指定"
        "loadImage" -> "(path)" to "画像を読み込む"
        "image" -> "(img, x, y)" to "画像を描画"
        "random" -> "(min, max)" to "乱数を生成"
        "map" -> "(value, a, b, c, d)" to "数値の範囲を変換"
        "translate" -> "(x, y)" to "座標を移動"
        "rotate" -> "(angle)" to "座標を回転"
        "text" -> "(str, x, y)" to "文字を描画"
        else -> return name
    }
    return name + detail.first + "\n" + text(detail.second)
}
