package com.hikariatelier.app

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorTransformsTest {
    @Test
    fun reindentsCodeAndIgnoresBracesInStringsAndLineComments() {
        val source = "function draw() {\nconst label = \"} // {\";\nif (true) { // }\nrect(1, 2, 3, 4);\n}\n}"
        val expected = "function draw() {\n  const label = \"} // {\";\n  if (true) { // }\n    rect(1, 2, 3, 4);\n  }\n}"
        assertEquals(expected, formatJavaScript(source))
        assertEquals(expected, formatJavaScript(expected))
    }

    @Test
    fun preservesMultilineTemplatesAndEscapedLineContinuationsExactly() {
        val template = "function draw() {\n const text = `first\n      second\n\n\n   last`;\n}"
        val continuedString = "function setup() {\n const text = 'first\\\n       second';\n}"
        assertEquals(template, formatJavaScript(template))
        assertEquals(continuedString, formatJavaScript(continuedString))
    }

    @Test
    fun preservesBlockCommentsAndRegexExactly() {
        val comment = "function setup() {\n/* sample {\n       indented example }\n*/\n}"
        val regex = "function setup() {\n const matcher = /[{}]/g;\n}"
        assertEquals(comment, formatJavaScript(comment))
        assertEquals(regex, formatJavaScript(regex))
    }

    @Test
    fun preservesIncompleteOrMismatchedCode() {
        listOf("function setup() {\n   draw();", "const x = [1, 2);", "const x = 'unfinished")
            .forEach { assertEquals(it, formatJavaScript(it)) }
    }

    @Test
    fun retainsCrlfLineEndingsAndHandlesMultipleLeadingClosers() {
        val source = "draw({\r\nthing: true\r\n});\r\n"
        assertEquals("draw({\r\n    thing: true\r\n});\r\n", formatJavaScript(source))
    }
}
