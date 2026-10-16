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

        assertTrue(html.contains("cdn.jsdelivr.net/npm/p5@1.11.5/lib/p5.min.js"))
        assertTrue(html.contains("cdn.jsdelivr.net/npm/p5@1.11.5/lib/addons/p5.sound.min.js"))
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
    fun esModuleSyntaxTriggersModuleScriptTagAndCallbackRegistration() {
        val work = Work(
            id = "test-5",
            title = "Module Sketch",
            code = "import { something } from './mod.js';\nexport function setup() { createCanvas(200, 200); }\nexport function draw() {}"
        )
        val html = StandaloneHtmlGenerator.generate(work)

        assertTrue(html.contains("<script type=\"module\">"))
        assertTrue(html.contains("__editRinRegisterModuleCallbacks"))
        assertTrue(html.contains("rin-project/sketch.js"))
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
                <link rel="stylesheet" href="style.css?t=123">
                <script src="p5-v2.min.js"></script>
                <script src="p5.webgpu.js"></script>
                <script src="sketch.js?v=2"></script>
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
                "style.css" to "body { background-color: purple; content: \"</style>\"; }",
                "sketch.js" to "function setup() { createCanvas(100, 100); }"
            )
        )
        val html = StandaloneHtmlGenerator.generate(work)

        assertFalse(html.contains("p5_host.js"))
        assertFalse(html.contains("p5_bootstrap.js"))
        assertTrue(html.contains("cdn.jsdelivr.net/npm/p5@2.3.4/lib/p5.min.js"))
        assertTrue(html.contains("cdn.jsdelivr.net/npm/p5@2.3.4/lib/p5.webgpu.js"))
        assertTrue(html.contains("<style>\nbody { background-color: purple; content: \"<\\/style>\"; }\n</style>"))
        assertTrue(html.contains("function setup() { createCanvas(100, 100); }"))
    }
    @Test fun exportedParametersIncludeSavedValuesBeforeSketch() {
        val work = Work("params", "Params", "// @rin number speed \"Speed\" 0 10 1 1\nfunction draw(){circle(rinParams.speed,0,1)}",
            parameterValues = mapOf("speed" to "7"))
        val html = StandaloneHtmlGenerator.generate(work)
        assertTrue(html.contains("window.rinParams={\"speed\":7}"))
        assertTrue(html.indexOf("window.rinParams=") < html.indexOf("function draw"))
    }

    @Test fun selectedModuleAndRelativeDependenciesAreEmbedded() {
        val work = Work("module", "Module", "throw new Error('wrong entry')", files = mutableMapOf(
            "edit-rin.json" to """{"executionMode":"module","moduleEntry":"src/main.mjs"}""",
            "src/main.mjs" to "import { size } from './math.mjs'; export function setup(){ createCanvas(size,size) }",
            "src/math.mjs" to "export const size = 256;"
        ))
        val html = StandaloneHtmlGenerator.generate(work)
        assertTrue(html.contains("import \"rin-project/src/main.mjs\""))
        assertTrue(html.contains("rin-project/src/math.mjs"))
        assertFalse(html.contains("<script>\nthrow new Error('wrong entry')"))
        val map = org.json.JSONObject(Regex("""<script type="importmap">(.*?)</script>""").find(html)!!.groupValues[1])
        val encoded = map.getJSONObject("imports").getString("rin-project/src/main.mjs").substringAfter(',')
        val module = String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        assertTrue(module.contains("from \"rin-project/src/math.mjs\""))
        assertTrue(module.contains("__editRinRegisterModuleCallbacks"))
    }

    @Test fun moduleImportRewriteLeavesTextAndCommentsAlone() {
        val source = "// import './math.mjs'\nconst text = \"from './math.mjs'\"; import('./math.mjs');"
        val rewritten = rewriteStandaloneModuleImports(source, "src/main.mjs", setOf("src/math.mjs"))
        assertTrue(rewritten.startsWith("// import './math.mjs'"))
        assertTrue(rewritten.contains("\"from './math.mjs'\""))
        assertTrue(rewritten.contains("import(\"rin-project/src/math.mjs\")"))
    }
}
