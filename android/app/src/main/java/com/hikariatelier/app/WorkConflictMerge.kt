package com.hikariatelier.app

internal data class WorkMergeResult(val works: List<Work>, val activeId: String, val copies: Int)

/** A concurrent edit is preserved as a new work, never silently chosen over either author. */
internal fun mergeConcurrentWorks(base: List<Work>, local: List<Work>, remote: List<Work>, activeId: String): WorkMergeResult {
    fun same(a: Work?, b: Work?): Boolean = when {
        a == null || b == null -> a == b
        else -> serializeWorkStore(listOf(a), a.id) == serializeWorkStore(listOf(b), b.id)
    }
    val before = base.associateBy { it.id }
    val edited = local.associateBy { it.id }
    val external = remote.associateBy { it.id }
    val merged = mutableListOf<Work>()
    var selected = activeId
    var copies = 0
    (remote.map { it.id } + local.map { it.id }).distinct().forEach { id ->
        val original = before[id]
        val mine = edited[id]
        val theirs = external[id]
        when {
            same(mine, original) -> theirs?.let { merged += snapshotWork(it) }
            same(theirs, original) || same(mine, theirs) -> mine?.let { merged += snapshotWork(it) }
            else -> {
                theirs?.let { merged += snapshotWork(it) }
                mine?.let {
                    val copy = Work(java.util.UUID.randomUUID().toString(), "${it.title} (local)", it.code,
                        files = it.files.toMutableMap(), assets = it.assets.toMap(), revisions = it.revisions.toMutableList(),
                        previewAspectRatio = it.previewAspectRatio, p5Version = it.p5Version,
                        p5SoundEnabled = it.p5SoundEnabled, libraries = it.libraries.toMap(),
                        parameterValues = it.parameterValues.toMap(), isPinned = it.isPinned, tags = it.tags.toList())
                    merged += copy
                    copies++
                    if (selected == id) selected = copy.id
                }
            }
        }
    }
    if (merged.none { it.id == selected }) selected = merged.firstOrNull()?.id.orEmpty()
    return WorkMergeResult(merged, selected, copies)
}
