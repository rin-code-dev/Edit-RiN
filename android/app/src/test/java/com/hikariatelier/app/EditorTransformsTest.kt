package com.hikariatelier.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `minifyJavaScriptForTweet golfs WEBGL torus sphere sketch with extreme tricks`() {
        val input = """
            const CANVAS_SIZE = 600;
            let time = 0;
            function setup() {
              createCanvas(CANVAS_SIZE, CANVAS_SIZE, WEBGL);
              pixelDensity(1);
            }
            function draw() {
              background(0);
              let p = PI / 40;
              noStroke();
              rotateX(0.5);
              rotateY(-0.5);
              let radius = 90;
              for (let y = 0; y < 40; y++) {
                for (let x = 0; x < 80; x++) {
                  push();
                  translate((2 + sin(v = (y + time) * p * 2)) * cos(u = (x + time) * p) * radius, (2 + sin(v)) * sin(u) * radius, z = cos(v) * radius);
                  sphere(cos(v) + 0.3);
                  pop();
                }
              }
              time = (time + 0.02) % 1;
            }
        """.trimIndent()

        val expected = """
            t=0
            draw=_=>{t||createCanvas(W=600,W,WEBGL)
            background(0)
            p=PI/40
            noStroke`#つぶやきProcessing`
            rotateX(.5)
            rotateY(-.5)
            r=90
            for(y=40;y--;)for(x=80;x--;pop(sphere(cos(v)+.3)))push(),translate((2+sin(v=(y+t)*p*2))*cos(u=(x+t)*p)*r,(2+sin(v))*sin(u)*r,z=cos(v)*r)
            t=(t+.02)%1}
        """.trimIndent()

        val actual = minifyJavaScriptForTweet(input)
        assertEquals(expected, actual)
    }

    @Test
    fun `minifyJavaScriptForTweet inlines constants and renames multi-char variables`() {
        val input = """
            const CANVAS_SIZE = 600;
            const RING_COUNT = 36;
            let time = 0;
            function setup() {
              createCanvas(CANVAS_SIZE, CANVAS_SIZE);
              pixelDensity(1);
              colorMode(HSB, 360, 100, 100, 100);
              strokeWeight(1.5);
              noFill();
            }
            function draw() {
              background(0);
              translate(width * 0.5, height * 0.5);
              for (let i = 0; i < RING_COUNT; i++) {
                circle(i * 4, 0, 50);
              }
              time += 0.01;
            }
        """.trimIndent()

        val actual = minifyJavaScriptForTweet(input)
        assertFalse(actual.contains("CANVAS_SIZE"))
        assertFalse(actual.contains("RING_COUNT"))
        assertFalse(actual.contains("pixelDensity"))
        assertTrue(actual.contains("t||createCanvas(W=600,W)"))
        assertTrue(actual.contains("noFill`#つぶやきProcessing`"))
        assertTrue(actual.contains("for(i=36;i--;)"))
    }
}
