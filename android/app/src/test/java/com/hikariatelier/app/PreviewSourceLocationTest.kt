package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class PreviewSourceLocationTest {
    @Test fun mapsSortedFilesAndMainWithoutLinkingSeparatorLines() {
        val files = previewSourceFiles("setup();\ndraw();", linkedMapOf("z.js" to "z();", "a.js" to "a();\n"))
        assertEquals(PreviewSourceLocation("a.js", 2), previewSourceLocation(files, 2))
        assertNull(previewSourceLocation(files, 3))
        assertEquals(PreviewSourceLocation("z.js", 1), previewSourceLocation(files, 4))
        assertNull(previewSourceLocation(files, 5))
        assertEquals(PreviewSourceLocation("sketch.js", 2), previewSourceLocation(files, 7))
        assertNull(previewSourceLocation(files, 8))
        assertNull(previewSourceLocation(files, 0))
    }

    @Test fun navigationHandlesEmptyLinesAndRejectsDeletedLines() {
        assertEquals(0, sourceLineOffset("", 1))
        assertEquals(3, sourceLineOffset("a\n\nb", 3))
        assertEquals(2, sourceLineOffset("a\n", 2))
        assertNull(sourceLineOffset("a", 2))
        assertNull(sourceLineOffset("a", 0))
    }
}
