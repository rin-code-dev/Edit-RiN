package com.hikariatelier.app

private val INLINE_WATCH_COMMENT_REGEX = Regex(
    """^(\s*)(?:(let|const|var)\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*([^;\n]+);?|([A-Za-z_$][A-Za-z0-9_$.]*)\s*=\s*([^;\n]+);?|([^/\n]+?))\s*//\s*\?\s*$""",
    RegexOption.MULTILINE
)

/**
 * Instruments lines ending with `// ?` so the runtime reports their live values via `window.rinWatch(line, label, val)`.
 * Preserves exact line count so stack traces and line numbers remain 1:1.
 */
internal fun instrumentInlineWatchComments(source: String): String {
    if (!source.contains("//?") && !source.contains("// ?")) return source
    val lines = source.split('\n')
    var changed = false
    val transformed = lines.mapIndexed { idx, line ->
        val trimmed = line.trimEnd()
        if (!trimmed.endsWith("//?") && !trimmed.endsWith("// ?")) {
            line
        } else {
            val match = INLINE_WATCH_COMMENT_REGEX.find(line)
            if (match == null) {
                line
            } else {
                changed = true
                val indent = match.groupValues[1]
                val declarationKind = match.groupValues[2]
                val declVar = match.groupValues[3]
                val declExpr = match.groupValues[4]
                val assignVar = match.groupValues[5]
                val assignExpr = match.groupValues[6]
                val bareExpr = match.groupValues[7].trim().removeSuffix(";")
                val lineNumber = idx + 1
                when {
                    declVar.isNotEmpty() ->
                        "${indent}$declarationKind $declVar = window.rinWatch ? window.rinWatch($lineNumber, ${org.json.JSONObject.quote(declVar)}, ($declExpr)) : ($declExpr); // ?"
                    assignVar.isNotEmpty() ->
                        "$indent$assignVar = window.rinWatch ? window.rinWatch($lineNumber, ${org.json.JSONObject.quote(assignVar)}, ($assignExpr)) : ($assignExpr); // ?"
                    bareExpr.isNotEmpty() ->
                        "${indent}window.rinWatch?.($lineNumber, ${org.json.JSONObject.quote(bareExpr.take(24))}, ($bareExpr)); // ?"
                    else -> line
                }
            }
        }
    }
    return if (changed) transformed.joinToString("\n") else source
}
