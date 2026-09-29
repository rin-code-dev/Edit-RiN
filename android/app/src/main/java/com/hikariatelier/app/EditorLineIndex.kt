package com.hikariatelier.app

/** Reuse unchanged line starts, scanning for newlines only inside the edited range. */
internal class EditorLineIndex {
    private var source = ""
    private var starts: List<Int> = listOf(0)

    fun update(next: String): List<Int> {
        if (next == source) return starts
        var prefix = 0
        while (prefix < minOf(source.length, next.length) && source[prefix] == next[prefix]) prefix++
        var suffix = 0
        while (suffix < minOf(source.length, next.length) - prefix &&
            source[source.lastIndex - suffix] == next[next.lastIndex - suffix]) suffix++
        val oldEnd = source.length - suffix
        val newEnd = next.length - suffix
        val delta = next.length - source.length
        starts = buildList {
            for (start in starts) {
                if (start > prefix) break
                add(start)
            }
            for (index in prefix until newEnd) if (next[index] == '\n') add(index + 1)
            for (start in starts) if (start > oldEnd) add(start + delta)
        }
        source = next
        return starts
    }
}
