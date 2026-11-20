package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class ProjectSearchTest {
    @Test fun searchesCurrentDraftsWithoutOtherWorksOrDeletedFiles() {
        val sources = projectSearchSources("work", "unsaved main", mapOf("b.js" to "saved", "a.js" to "saved"),
            mapOf("work/b.js" to "unsaved helper", "other/a.js" to "wrong work", "work/deleted.js" to "deleted"))
        assertEquals(listOf("sketch.js", "a.js", "b.js"), sources.keys.toList())
        assertEquals(listOf("sketch.js", "b.js"), searchProject(sources, "unsaved", false).matches.map { it.file })
        assertEquals("saved", sources["a.js"])
        assertFalse(sources.containsKey("deleted.js"))
    }

    @Test fun returnsExactSelectionsAndLinesForMultipleMatchesAndUnicode() {
        val code = "日本語\r\n\nlet Foo = foo + foo;"
        val matches = searchProject(mapOf("helper.js" to code), "foo", false).matches
        assertEquals(3, matches.size)
        assertTrue(matches.all { it.line == 3 && it.excerpt == "let Foo = foo + foo;" })
        assertEquals(listOf("Foo", "foo", "foo"), matches.map { code.substring(it.start, it.end) })
        assertEquals(2, searchProject(mapOf("helper.js" to code), "foo", true).matches.size)
    }

    @Test fun handlesEmptyQueriesMultilineMatchesAndResultLimit() {
        val sources = mapOf("sketch.js" to "a\na\na")
        assertTrue(searchProject(sources, "", false).matches.isEmpty())
        assertEquals(1, searchProject(sources, "a\na", false).matches.single().line)
        assertTrue(searchProject(sources, "a", false, 2).truncated)
        assertFalse(searchProject(sources, "a", false, 3).truncated)
        assertTrue(searchProject(sources, "absent", false).matches.isEmpty())
    }

    @Test fun excerptsStayBoundedAndIncludeMatchesFarAlongALine() {
        val code = "x".repeat(2000) + "needle" + "y".repeat(2000)
        val match = searchProject(mapOf("long.js" to code), "needle", true).matches.single()
        assertTrue(match.excerpt.contains("needle"))
        assertTrue(match.excerpt.length <= 202)
        assertEquals("needle", code.substring(match.start, match.end))
    }

    @Test fun findsCompactGalleryCodeMatchesAcrossFilesAndMultiTokenQueries() {
        val sources = mapOf(
            "sketch.js" to "function setup() {\n  createCanvas(400, 400);\n}\nfunction draw() {\n  circle(200, 200, 50);\n}",
            "shader.frag" to "precision mediump float;\nfloat n = perlinNoise(uv);\nfloat m = perlinNoise(uv * 2.0);"
        )
        val fragMatch = findFirstCodeMatchInSources("w1", sources, "perlinNoise")!!
        assertEquals("shader.frag", fragMatch.file)
        assertEquals(2, fragMatch.line)
        assertEquals(2, fragMatch.matchCount)
        assertTrue(fragMatch.excerpt.contains("perlinNoise"))

        val multiLineMatch = findFirstCodeMatchInSources("w1", sources, "createCanvas circle")!!
        assertEquals("sketch.js", multiLineMatch.file)
        assertEquals(2, multiLineMatch.line)
        assertEquals("createCanvas", multiLineMatch.matchedToken)

        assertNull(findFirstCodeMatchInSources("w1", sources, "nonexistentToken"))
        assertNull(findFirstCodeMatchInSources("w1", sources, "   "))
    }

    @Test fun extractsCodeOutlineForJsFunctionsClassesArrowsSectionsAndGlsl() {
        val js = """
            // --- Setup & State ---
            /*
            function ignoredInBlockComment() {}
            */
            function setup() {
              createCanvas(400, 400);
            }
            class Particle {}
            const updateField = (dt) => dt * 2;
            // === Render ===
            async function draw() {}
        """.trimIndent()
        val jsOutline = extractCodeOutline(js, "sketch.js")
        assertEquals(
            listOf(
                CodeOutlineItem(CodeOutlineKind.SECTION, "Setup & State", 1),
                CodeOutlineItem(CodeOutlineKind.FUNCTION, "setup()", 5),
                CodeOutlineItem(CodeOutlineKind.CLASS, "Particle", 8),
                CodeOutlineItem(CodeOutlineKind.FUNCTION, "updateField()", 9),
                CodeOutlineItem(CodeOutlineKind.SECTION, "Render", 10),
                CodeOutlineItem(CodeOutlineKind.FUNCTION, "draw()", 11)
            ),
            jsOutline
        )

        val glsl = """
            precision mediump float;
            // --- Noise ---
            float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
            void main() { gl_FragColor = vec4(1.0); }
        """.trimIndent()
        val glslOutline = extractCodeOutline(glsl, "effect.frag")
        assertEquals(
            listOf(
                CodeOutlineItem(CodeOutlineKind.SECTION, "Noise", 2),
                CodeOutlineItem(CodeOutlineKind.FUNCTION, "hash()", 3),
                CodeOutlineItem(CodeOutlineKind.FUNCTION, "main()", 4)
            ),
            glslOutline
        )
    }
}
