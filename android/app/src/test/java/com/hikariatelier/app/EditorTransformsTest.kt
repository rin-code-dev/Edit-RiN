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

    @Test
    fun reindentsCodeWithDivisionExpressionsCorrectly() {
        val source = "function draw() {\nif (x > width / 2) {\ncircle(width / 2, height / 2, 50);\n} else {\nrect(0, 0, width / 4, height / 4);\n}\n}"
        val expected = "function draw() {\n  if (x > width / 2) {\n    circle(width / 2, height / 2, 50);\n  } else {\n    rect(0, 0, width / 4, height / 4);\n  }\n}"
        assertEquals(expected, formatJavaScript(source))
    }

    @Test
    fun protectsTemplateWhitespaceAndParsesCodeAfterMultilineComments() {
        val template = "function setup() {\nconst text = `first   \nsecond`;\n}"
        val comment = "function setup() {\n/* first\n*/ rect(1, 2, 3, 4); }"
        assertEquals(FormatResult.Success(template), formatJavaScriptDetailed(template))
        assertEquals(FormatResult.Success(comment), formatJavaScriptDetailed(comment))
    }

    @Test
    fun acceptsContinuedStringsAndNestedTemplateInterpolation() {
        val continued = "function setup() {\nconst text = 'first\\\nsecond';\n}"
        val nested = "const text = `a \${`b \${({ x: 1 }).x}`} c`;"
        assertEquals(FormatResult.Success(continued), formatJavaScriptDetailed(continued))
        assertEquals(FormatResult.Success(nested), formatJavaScriptDetailed(nested))
        assertEquals(FormatResult.UnfinishedString, formatJavaScriptDetailed("const text = 'first\\"))
    }

    @Test
    fun keepsDivisionContextAcrossLinesAndPostfixOperators() {
        val source = "function draw() {\nconst x = width\n/ 2;\nconst y = x++ / 2;\n}"
        assertEquals("function draw() {\n  const x = width\n  / 2;\n  const y = x++ / 2;\n}", formatJavaScript(source))
    }
    @Test
    fun mapsCursorWithIndentationAndRetainsBlankLinesAndCrlf() {
        val source = "function draw() {\r\nrect(1, 2, 3, 4);\r\n\r\n\r\n}"
        val formatted = formatJavaScript(source)
        assertEquals(source.count { it == '\n' }, formatted.count { it == '\n' })
        val cursor = source.indexOf("rect") + 4
        assertEquals(formatted.indexOf("rect") + 4, formattedJavaScriptOffset(source, formatted, cursor))
        assertEquals(0, formattedJavaScriptOffset(source, formatted, 0))
    }
}
