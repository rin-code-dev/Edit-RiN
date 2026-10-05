package com.hikariatelier.app

internal data class RemovedGalleryWork(val index: Int, val work: Work)
internal data class DeletedWorkBatch(val token: Long, val folder: String?, val removed: List<RemovedGalleryWork>)

/** Copies never share mutable authored state or IDs with the source. */
internal fun copyGalleryWork(source: Work, titles: Set<String>): Work {
    require(source.bodyLoaded)
    val base = "${source.title} copy"
    var title = base
    var suffix = 2
    while (title in titles) title = "$base ${suffix++}"
    return newGalleryWork(source, title)
}

private fun newGalleryWork(source: Work, title: String = source.title): Work = Work(
    id = java.util.UUID.randomUUID().toString(), title = title, code = source.code,
    files = source.files.toMutableMap(), assets = source.assets.toMap(),
    previewAspectRatio = source.previewAspectRatio, p5Version = source.p5Version,
    p5SoundEnabled = source.p5SoundEnabled, libraries = source.libraries.toMap(),
    parameterValues = source.parameterValues.toMap(), isPinned = source.isPinned,
    tags = source.tags.toList()
)

/** Restore only deleted works; retain newer edits, additions, selection and editor Undo. */
internal fun restoreGalleryWorks(current: List<Work>, removed: List<RemovedGalleryWork>): List<Work> {
    require(removed.isNotEmpty() && removed.all { it.work.bodyLoaded })
    val ids = current.map { it.id }.toMutableSet()
    require(removed.all { ids.add(it.work.id) }) { "Deleted ID already exists" }
    return current.toMutableList().apply {
        removed.sortedBy { it.index }.forEach { insert ->
            add(insert.index.coerceIn(0, size), snapshotWork(insert.work))
        }
    }
}

/** Single- and multi-work archives both import as additions, with fresh IDs and matching history. */
internal fun reidentifyGalleryImport(backup: AssetBackup): AssetBackup {
    require(backup.store.works.isNotEmpty())
    val imported = backup.store.works.map { original -> original to newGalleryWork(original) }
    return AssetBackup(WorkStore(imported.map { it.second }, imported.first().second.id), null,
        imported.associate { (original, copy) -> copy.id to backup.snapshots[original.id].orEmpty() })
}
