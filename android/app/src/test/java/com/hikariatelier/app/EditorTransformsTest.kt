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
        assertTrue(actual.contains("t||(createCanvas(W=600,W),colorMode(HSB),strokeWeight(1.5),noFill`#つぶやきProcessing`)") || actual.contains("t||(createCanvas(W=600,W)"))
        assertTrue(actual.contains("for(i=36;i--;)"))
    }

    @Test
    fun `minifyJavaScriptForTweet guarantees t=0 when globals exist and setup has createCanvas`() {
        val input = """
            let count = 10;
            function setup() {
              createCanvas(400, 400);
            }
            function draw() {
              background(0);
              circle(200, 200, count);
            }
        """.trimIndent()
        val actual = minifyJavaScriptForTweet(input)
        assertTrue("Expected t=0 to be emitted at top, got: $actual", actual.startsWith("t=0\n") || actual.startsWith("t=0;"))
        assertTrue("Expected t||( or t||createCanvas, got: $actual", actual.contains("t||createCanvas(W=(w=200)*2,W)"))
        assertTrue("Expected ++t at the end, got: $actual", actual.contains("++t"))
    }

    @Test
    fun `minifyJavaScriptForTweet protects floating point numbers from canvas size replacement`() {
        val input = """
            function setup() {
              createCanvas(400, 400);
            }
            function draw() {
              background(0);
              let val1 = 400.5;
              let val2 = 0.400;
              let val3 = 200.25;
              circle(200, 200, val1);
            }
        """.trimIndent()
        val actual = minifyJavaScriptForTweet(input)
        assertTrue("Expected 400.5 to be preserved, got: $actual", actual.contains("400.5"))
        assertTrue("Expected 0.4 or .4 to be preserved, got: $actual", actual.contains(".4") || actual.contains("0.4"))
        assertTrue("Expected 200.25 to be preserved, got: $actual", actual.contains("200.25"))
        assertFalse("Did not expect W.5, got: $actual", actual.contains("W.5"))
    }

    @Test
    fun `minifyJavaScriptForTweet preserves p5 event callbacks and excludes clear from tagged templates`() {
        val input = """
            function setup() {
              createCanvas(400, 400);
            }
            function draw() {
              clear();
            }
            function mousePressed() {
              background(255);
            }
        """.trimIndent()
        val actual = minifyJavaScriptForTweet(input)
        assertTrue("Expected mousePressed to be retained without renaming, got: $actual", actual.contains("mousePressed"))
        assertFalse("clear() should not be tagged with template hashtag, got: $actual", actual.contains("clear`#つぶやきProcessing`"))
        assertTrue("Fallback comment hashtag should be present, got: $actual", actual.contains("#つぶやきProcessing"))
    }

    @Test
    fun `minifyJavaScriptForTweet does not duplicate ++t when draw already has ++t`() {
        val input = """
            function setup() {
              createCanvas(400, 400);
            }
            function draw() {
              background(0);
              ++t;
            }
        """.trimIndent()
        val actual = minifyJavaScriptForTweet(input)
        val count = Regex("""\+\+t""").findAll(actual).count()
        assertEquals("Expected exactly one ++t in output, got: $actual", 1, count)
    }

    @Test
    fun `minifyJavaScriptForTweet handles setup with loops using if(!t) block`() {
        val input = """
            let pts = [];
            function setup() {
              createCanvas(400, 400);
              for (let i = 0; i < 10; i++) {
                pts.push(i);
              }
            }
            function draw() {
              background(0);
            }
        """.trimIndent()
        val actual = minifyJavaScriptForTweet(input)
        assertTrue("Expected if(!t) block for loop in setup, got: $actual", actual.contains("if(!t){"))
        assertFalse("Should not create broken comma expression for loop, got: $actual", actual.contains("t||(createCanvas"))
    }
}
