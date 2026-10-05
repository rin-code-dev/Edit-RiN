package com.hikariatelier.app

internal const val SAMPLE_FOLDER = "\u0000samples"

/** Only bundled definitions create originals. Loaded stores never acquire this flag. */
internal fun sampleCatalog(definitions: List<Work>, users: List<Work>): List<Work> {
    val ids = users.map { it.id }.toMutableSet()
    return definitions.map { source ->
        var id = "builtin-sample:${source.id.removePrefix("builtin-sample:").trimEnd(':')}"
        while (!ids.add(id)) id += ":"
        Work(id, source.title, source.code, files = source.files.toMutableMap(), assets = source.assets.toMap(),
            previewAspectRatio = source.previewAspectRatio, p5Version = source.p5Version,
            p5SoundEnabled = source.p5SoundEnabled, libraries = source.libraries.toMap(),
            parameterValues = source.parameterValues.toMap(), createdAt = source.createdAt,
            updatedAt = source.updatedAt, isSample = true)
    }
}

internal fun userWorkStore(works: List<Work>, activeId: String): WorkStore {
    val users = works.filterNot { it.isSample }
    return WorkStore(users, activeId.takeIf { id -> users.any { it.id == id } } ?: users.firstOrNull()?.id.orEmpty())
}

internal fun validGalleryFolder(name: String): Boolean =
    name.isNotBlank() && name.length <= 60 && name.none { it.isISOControl() || it == '/' || it == '\\' }
