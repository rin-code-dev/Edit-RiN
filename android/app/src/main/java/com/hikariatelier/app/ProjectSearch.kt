package com.hikariatelier.app

internal data class ProjectSearchMatch(
    val file: String,
    val line: Int,
    val start: Int,
    val end: Int,
    val excerpt: String
)

internal data class ProjectSearchResults(val matches: List<ProjectSearchMatch>, val truncated: Boolean)

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
