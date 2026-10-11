package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class StandaloneHtmlGeneratorTest {

    @Test
    fun defaultSketchGeneratesValidP5V2Html() {
        val work = Work(
            id = "test-1",
            title = "My Test Sketch",
            code = "function setup() { createCanvas(400, 400); }\nfunction draw() { background(220); }"
        )
        val html = StandaloneHtmlGenerator.generate(work)

        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("<title>My Test Sketch</title>"))
        assertTrue(html.contains("cdn.jsdelivr.net/npm/p5@2.3.4/lib/p5.min.js"))
        assertFalse(html.contains("p5.sound"))
        assertTrue(html.contains("function setup() { createCanvas(400, 400); }"))
        assertTrue(html.contains("touch-action: none;"))
    }

    @Test
    fun legacySketchWithSoundIncludesV1Cdns() {
        val work = Work(
            id = "test-2",
            title = "Legacy Audio",
            code = "function setup() {}",
            p5Version = P5_VERSION_LEGACY,
            p5SoundEnabled = true
        )
        val html = StandaloneHtmlGenerator.generate(work)

        assertTrue(html.contains("cdn.jsdelivr.net/npm/p5@1.11.11/lib/p5.min.js"))
        assertTrue(html.contains("cdn.jsdelivr.net/npm/p5@1.11.11/lib/addons/p5.sound.min.js"))
    }

    @Test
    fun modernSketchWithSoundIncludesV2SoundCdn() {
        val work = Work(
            id = "test-2b",
            title = "Modern Audio",
            code = "function setup() {}",
            p5Version = P5_VERSION_CURRENT,
            p5SoundEnabled = true
        )
        val html = StandaloneHtmlGenerator.generate(work)

        assertTrue(html.contains("cdn.jsdelivr.net/npm/p5@2.3.4/lib/p5.min.js"))
        assertTrue(html.contains("cdn.jsdelivr.net/npm/p5.sound@0.4.1/dist/p5.sound.min.js"))
    }

    @Test
    fun libraryAddonsMatterAndBrushAreIncluded() {
        val work = Work(
            id = "test-3",
            title = "Physics & Brush",
            code = "function setup() {}",
            p5Version = P5_VERSION_CURRENT,
            libraries = mapOf("matter-js" to "0.20.0", "p5.brush" to "2.2.1")
        )
        val html = StandaloneHtmlGenerator.generate(work)

        assertTrue(html.contains("cdnjs.cloudflare.com/ajax/libs/matter-js/0.20.0/matter.min.js"))
        assertTrue(html.contains("cdn.jsdelivr.net/npm/p5.brush@2.2.1/dist/p5.brush.js"))
    }

    @Test
    fun auxiliaryScriptsAreInlinedBeforeMainCode() {
        val work = Work(
            id = "test-4",
            title = "Multi File",
            code = "function setup() { helper(); }",
            files = mutableMapOf(
                "helper.js" to "function helper() { console.log('hello'); }"
            )
        )
        val html = StandaloneHtmlGenerator.generate(work)

        assertTrue(html.contains("<!-- Project file: helper.js -->"))
        assertTrue(html.contains("function helper() { console.log('hello'); }"))
        assertTrue(html.indexOf("function helper()") < html.indexOf("function setup()"))
    }

    @Test
    fun esModuleSyntaxTriggersModuleScriptTag() {
        val work = Work(
            id = "test-5",
            title = "Module Sketch",
            code = "import { something } from './mod.js';\nexport function setup() {}"
        )
        val html = StandaloneHtmlGenerator.generate(work)

        assertTrue(html.contains("<script type=\"module\">"))
    }

    @Test
    fun scriptEndTagsInCodeAreEscapedSafely() {
        val work = Work(
            id = "test-6",
            title = "XSS Test",
            code = "const t = '</script><script>alert(1)</script>';"
        )
        val html = StandaloneHtmlGenerator.generate(work)

        assertTrue(html.contains("<\\/script>"))
        val scriptTagOpen = html.lastIndexOf("<script>")
        val scriptTagClose = html.lastIndexOf("</script>")
        val bodyContent = html.substring(scriptTagOpen, scriptTagClose)
        assertFalse("Unescaped closing script tag found inside script block", bodyContent.contains("</script><script>"))
    }

    @Test
    fun existingIndexHtmlProjectsAreInlinedAndSanitized() {
        val existingHtml = """
            <!doctype html>
            <html>
            <head>
                <script src="/__edit-rin__/p5_host.js"></script>
                <script src="/__edit-rin__/p5_bootstrap.js"></script>
                <link rel="stylesheet" href="style.css">
                <script src="p5-v2.min.js"></script>
                <script src="sketch.js"></script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val work = Work(
            id = "test-7",
            title = "HTML Project",
            code = "function setup() { createCanvas(100, 100); }",
            files = mutableMapOf(
                "index.html" to existingHtml,
                "style.css" to "body { background-color: purple; }",
                "sketch.js" to "function setup() { createCanvas(100, 100); }"
            )
        )
        val html = StandaloneHtmlGenerator.generate(work)

        assertFalse(html.contains("p5_host.js"))
        assertFalse(html.contains("p5_bootstrap.js"))
        assertTrue(html.contains("cdn.jsdelivr.net/npm/p5@2.3.4/lib/p5.min.js"))
        assertTrue(html.contains("<style>\nbody { background-color: purple; }\n</style>"))
        assertTrue(html.contains("function setup() { createCanvas(100, 100); }"))
    }
}
