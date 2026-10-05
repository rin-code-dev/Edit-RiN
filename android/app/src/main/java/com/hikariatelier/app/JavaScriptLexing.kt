package com.hikariatelier.app

/** Shared by completion and execution-mode detection; escaped slashes/classes are not delimiters. */
internal fun javaScriptRegexLiteralEnd(source: String, start: Int): Int? {
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
