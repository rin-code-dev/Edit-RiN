package com.hikariatelier.app

/** Reuse unchanged line starts, scanning for newlines only inside the edited range. */
internal class EditorLineIndex {
    private var source = ""
    private var starts: List<Int> = listOf(0)

    fun update(next: String, shared: EditorDocumentChange? = null): List<Int> {
        if (next == source) return starts
        val change = shared?.takeIf { it.before == source && it.after == next }?.range
            ?: editorTextChange(androidx.compose.ui.text.input.TextFieldValue(source),
                androidx.compose.ui.text.input.TextFieldValue(next))
        val prefix = change.start
        val oldEnd = change.oldEnd
        val newEnd = change.newEnd
        val delta = change.delta
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
