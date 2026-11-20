package com.hikariatelier.app

internal data class ProjectSearchMatch(
    val file: String,
    val line: Int,
    val start: Int,
    val end: Int,
    val excerpt: String
)

internal data class ProjectSearchResults(val matches: List<ProjectSearchMatch>, val truncated: Boolean)

internal data class GalleryCodeMatch(
    val workId: String,
    val file: String,
    val line: Int,
    val excerpt: String,
    val matchCount: Int,
    val matchedToken: String
)

internal fun projectSearchSources(
    workId: String,
    mainCode: String,
    files: Map<String, String>,
    drafts: Map<String, String>
): Map<String, String> = buildMap {
    put("sketch.js", mainCode)
    files.toSortedMap().forEach { (name, saved) ->
        if (name != "sketch.js") put(name, drafts["$workId/$name"] ?: saved)
    }
}

internal fun searchProject(
    sources: Map<String, String>,
    query: String,
    matchCase: Boolean,
    limit: Int = 500
): ProjectSearchResults {
    if (query.isEmpty()) return ProjectSearchResults(emptyList(), false)
    require(limit > 0)
    val matches = mutableListOf<ProjectSearchMatch>()
    for ((file, code) in sources) {
        var from = 0
        var line = 1
        var scanned = 0
        while (from <= code.length - query.length) {
            val start = code.indexOf(query, from, ignoreCase = !matchCase)
            if (start < 0) break
            if (matches.size == limit) return ProjectSearchResults(matches, true)
            while (scanned < start) {
                if (code[scanned] == '\n') line++
                scanned++
            }
            val lineStart = if (start == 0) 0 else code.lastIndexOf('\n', start - 1) + 1
            val excerptStart = maxOf(lineStart, start - 60)
            val lineEnd = code.indexOf('\n', start).let { if (it < 0) code.length else it }
            val excerptEnd = minOf(lineEnd, excerptStart + 200)
            val excerpt = (if (excerptStart > lineStart) "…" else "") +
                code.substring(excerptStart, excerptEnd).trimEnd('\r') +
                (if (excerptEnd < lineEnd) "…" else "")
            matches += ProjectSearchMatch(file, line, start, start + query.length, excerpt)
            from = start + query.length
        }
    }
    return ProjectSearchResults(matches, false)
}

private fun formatCompactCodeExcerpt(lineText: String, matchStart: Int, matchLength: Int, maxLength: Int = 76): String {
    val cleanLine = lineText.trimEnd('\r')
    val leadingSpaces = cleanLine.takeWhile { it == ' ' || it == '\t' }.length
    val trimmed = cleanLine.substring(leadingSpaces)
    val adjustedStart = (matchStart - leadingSpaces).coerceAtLeast(0)
    if (trimmed.length <= maxLength) return trimmed
    val half = ((maxLength - matchLength) / 2).coerceAtLeast(12)
    val start = (adjustedStart - half).coerceIn(0, (trimmed.length - maxLength).coerceAtLeast(0))
    val end = minOf(trimmed.length, start + maxLength)
    return (if (start > 0) "…" else "") + trimmed.substring(start, end) + (if (end < trimmed.length) "…" else "")
}

internal fun findFirstCodeMatchInSources(
    workId: String,
    sources: Map<String, String>,
    rawQuery: String
): GalleryCodeMatch? {
    val query = rawQuery.trim()
    if (query.isEmpty()) return null
    val tokens = query.split(Regex("\\s+")).filter { it.isNotBlank() }.take(8)
    if (tokens.isEmpty()) return null

    val orderedFiles = buildList {
        if ("sketch.js" in sources) add("sketch.js")
        addAll(sources.keys.filter { it != "sketch.js" }.sorted())
    }

    var firstFile: String? = null
    var firstLine = 1
    var firstExcerpt = ""
    var firstToken = query
    var totalMatches = 0

    for (file in orderedFiles) {
        val code = sources[file] ?: continue
        var lineNumber = 0
        for (rawLine in code.lineSequence()) {
            lineNumber++
            val exactIndex = rawLine.indexOf(query, ignoreCase = true)
            val hit: Pair<Int, String>? = if (exactIndex >= 0) {
                exactIndex to query
            } else if (tokens.size > 1 && tokens.all { rawLine.contains(it, ignoreCase = true) }) {
                val idx = rawLine.indexOf(tokens.first(), ignoreCase = true)
                if (idx >= 0) idx to tokens.first() else null
            } else null
            if (hit != null) {
                if (firstFile == null) {
                    firstFile = file
                    firstLine = lineNumber
                    firstToken = hit.second
                    firstExcerpt = formatCompactCodeExcerpt(rawLine, hit.first, hit.second.length)
                }
                totalMatches++
                if (totalMatches >= 99) break
            }
        }
        if (totalMatches >= 99) break
    }

    // Multi-token fallback across separate lines when tokens appear in different lines of the same work.
    if (firstFile == null && tokens.size > 1) {
        val combinedMatchesAll = tokens.all { token ->
            orderedFiles.any { file -> sources[file]?.contains(token, ignoreCase = true) == true }
        }
        if (combinedMatchesAll) {
            val probe = tokens.first()
            for (file in orderedFiles) {
                val code = sources[file] ?: continue
                var lineNumber = 0
                for (rawLine in code.lineSequence()) {
                    lineNumber++
                    val idx = rawLine.indexOf(probe, ignoreCase = true)
                    if (idx >= 0) {
                        if (firstFile == null) {
                            firstFile = file
                            firstLine = lineNumber
                            firstToken = probe
                            firstExcerpt = formatCompactCodeExcerpt(rawLine, idx, probe.length)
                        }
                        totalMatches++
                        if (totalMatches >= 99) break
                    }
                }
                if (totalMatches >= 99) break
            }
        }
    }

    val matchedFile = firstFile ?: return null
    return GalleryCodeMatch(
        workId = workId,
        file = matchedFile,
        line = firstLine,
        excerpt = firstExcerpt,
        matchCount = totalMatches,
        matchedToken = firstToken
    )
}

internal fun findFirstCodeMatchInCombinedCode(
    workId: String,
    combinedCode: String,
    rawQuery: String
): GalleryCodeMatch? = findFirstCodeMatchInSources(workId, mapOf("sketch.js" to combinedCode), rawQuery)

internal enum class CodeOutlineKind { FUNCTION, CLASS, SECTION }

internal data class CodeOutlineItem(
    val kind: CodeOutlineKind,
    val name: String,
    val line: Int
)

private val OUTLINE_SECTION_REGEX = Regex("""^\s*//\s*(?:---+|\===+|#|MARK:)\s*(.+?)\s*(?:---+|\===+)?\s*$""")
private val OUTLINE_JS_FUNCTION_REGEX = Regex("""^\s*(?:export\s+(?:default\s+)?)?(?:async\s+)?function\s*\*?\s*([A-Za-z_$][A-Za-z0-9_$]*)\s*\(""")
private val OUTLINE_JS_CLASS_REGEX = Regex("""^\s*(?:export\s+(?:default\s+)?)?class\s+([A-Za-z_$][A-Za-z0-9_$]*)\b""")
private val OUTLINE_JS_ARROW_REGEX = Regex("""^\s*(?:export\s+)?(?:const|let|var)\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*(?:async\s*)?(?:\([^)]*\)|[A-Za-z_$][A-Za-z0-9_$]*)\s*=>""")
private val OUTLINE_JS_FUNC_EXPR_REGEX = Regex("""^\s*(?:export\s+)?(?:const|let|var)\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*(?:async\s+)?function\b""")
private val OUTLINE_GLSL_FUNCTION_REGEX = Regex("""^\s*(?:(?:lowp|mediump|highp)\s+)?(?:void|bool|int|uint|float|double|vec[234]|ivec[234]|uvec[234]|bvec[234]|mat[234](?:x[234])?)\s+([A-Za-z_][A-Za-z0-9_]*)\s*\([^)]*\)\s*(?:\{|$)""")

internal fun extractCodeOutline(source: String, file: String = "sketch.js"): List<CodeOutlineItem> {
    if (source.isBlank()) return emptyList()
    val syntax = projectTextSyntax(file)
    val items = mutableListOf<CodeOutlineItem>()
    var inBlockComment = false
    var lineNumber = 0
    for (rawLine in source.lineSequence()) {
        lineNumber++
        val line = rawLine.trimEnd('\r')
        if (inBlockComment) {
            val endIdx = line.indexOf("*/")
            if (endIdx >= 0) inBlockComment = false
            continue
        }
        val trimmed = line.trimStart()
        if (trimmed.startsWith("/*")) {
            if (trimmed.indexOf("*/", startIndex = 2) < 0) inBlockComment = true
            continue
        }
        OUTLINE_SECTION_REGEX.matchEntire(line)?.let { match ->
            val title = match.groupValues[1].trim()
            if (title.isNotEmpty()) items += CodeOutlineItem(CodeOutlineKind.SECTION, title, lineNumber)
            continue
        }
        if (trimmed.startsWith("//")) continue
        if (syntax == ProjectTextSyntax.SHADER) {
            OUTLINE_GLSL_FUNCTION_REGEX.find(line)?.let { match ->
                val name = match.groupValues[1]
                if (name !in setOf("if", "for", "while", "switch")) {
                    items += CodeOutlineItem(CodeOutlineKind.FUNCTION, "$name()", lineNumber)
                }
            }
        } else if (syntax == ProjectTextSyntax.JAVASCRIPT || syntax == ProjectTextSyntax.PLAIN) {
            OUTLINE_JS_FUNCTION_REGEX.find(line)?.let { match ->
                items += CodeOutlineItem(CodeOutlineKind.FUNCTION, "${match.groupValues[1]}()", lineNumber)
                continue
            }
            OUTLINE_JS_CLASS_REGEX.find(line)?.let { match ->
                items += CodeOutlineItem(CodeOutlineKind.CLASS, match.groupValues[1], lineNumber)
                continue
            }
            (OUTLINE_JS_ARROW_REGEX.find(line) ?: OUTLINE_JS_FUNC_EXPR_REGEX.find(line))?.let { match ->
                items += CodeOutlineItem(CodeOutlineKind.FUNCTION, "${match.groupValues[1]}()", lineNumber)
            }
        }
    }
    return items
}
