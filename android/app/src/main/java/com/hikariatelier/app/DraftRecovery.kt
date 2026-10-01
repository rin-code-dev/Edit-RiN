package com.hikariatelier.app

/** Explicit recovery makes independent works, including auxiliary drafts of other work IDs. */
internal fun recoveredDraftWorks(snapshot: DraftSnapshot, files: Map<String, String>, saved: List<Work>, sameStore: Boolean): List<Work> {
    val grouped = files.entries.groupBy { (key, _) ->
        saved.sortedByDescending { it.id.length }.firstOrNull { key.startsWith("${it.id}/") }?.id
            ?: key.substringBeforeLast('/', snapshot.workId)
    }
    return (listOf(snapshot.workId) + grouped.keys).distinct().map { id ->
        val base = if (sameStore) saved.firstOrNull { it.id == id } else null
        val sources = base?.files?.toMutableMap() ?: mutableMapOf()
        grouped[id].orEmpty().forEach { (key, code) -> sources[key.removePrefix("$id/")] = code }
        Work(java.util.UUID.randomUUID().toString(), "${base?.title ?: "Recovered draft"} (draft)",
            if (id == snapshot.workId) snapshot.code else base?.code.orEmpty(), files = sources,
            assets = base?.assets.orEmpty(), previewAspectRatio = base?.previewAspectRatio ?: "1:1",
            p5Version = base?.p5Version ?: P5_VERSION_CURRENT, p5SoundEnabled = base?.p5SoundEnabled == true,
            libraries = base?.libraries.orEmpty(), parameterValues = base?.parameterValues.orEmpty())
    }
}
