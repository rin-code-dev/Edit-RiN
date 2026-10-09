package com.hikariatelier.app

/** Main-thread queue; priority changes never restart a render already in progress. */
internal class ThumbnailQueue<T> {
    private var current = emptyMap<String, T>()
    private val completed = mutableMapOf<String, T>()
    private val pending = linkedSetOf<String>()
    private val running = mutableSetOf<String>()
    val hasPending get() = pending.isNotEmpty()
    fun replace(values: Map<String, T>) {
        current = values
        completed.keys.retainAll(values.keys); pending.retainAll(values.keys)
        values.forEach { (id, value) -> if (completed[id] != value && id !in running) pending.add(id) }
    }
    fun invalidate(id: String) {
        completed.remove(id)
        if (id in current) pending.add(id)
    }
    fun claim(priority: List<String>, active: String, excluded: Set<String> = emptySet()): Pair<String, T>? {
        val id = nextThumbnailId(pending - running - excluded, priority, active) ?: return null
        pending.remove(id); running.add(id)
        return id to current.getValue(id)
    }
    fun finish(id: String, value: T, success: Boolean) {
        running.remove(id)
        if (success && current[id] == value) completed[id] = value
        else if (id in current) pending.add(id)
    }
    fun matches(id: String, value: T) = current[id] == value
    fun ids() = current.keys
}
