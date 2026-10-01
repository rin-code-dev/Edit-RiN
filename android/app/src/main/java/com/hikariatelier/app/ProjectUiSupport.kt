package com.hikariatelier.app

internal fun dirtyProjectFiles(
    workId: String, savedMain: String, currentMain: String,
    savedFiles: Map<String, String>, drafts: Map<String, String>
): Set<String> = buildSet {
    if (savedMain != currentMain) add("sketch.js")
    savedFiles.forEach { (name, code) ->
        if (drafts["$workId/$name"]?.let { it != code } == true) add(name)
    }
}

/** Textual reference candidates, not a JavaScript parser or a guarantee of every runtime path. */
internal fun assetReferenceCandidates(sources: Map<String, String>, name: String): ProjectSearchResults =
    searchProject(sources, "assets/$name", matchCase = true, limit = 50)

internal fun matchingUserTemplates(templates: List<Work>, query: String): List<Work> {
    val trimmed = query.trim()
    return if (trimmed.isEmpty()) templates else templates.filter { it.title.contains(trimmed, ignoreCase = true) }
}
