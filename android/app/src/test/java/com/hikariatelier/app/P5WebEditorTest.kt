package com.hikariatelier.app

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P5WebEditorTest {
    @Test
    fun parsesFolderPathsAndImportsCodeWithAssets() {
        val json = """
            {"projects":[{
              "id":"project-1","name":"Imported drawing","files":[
                {"id":"root","name":"root","fileType":"folder","children":["main","helper","assets"]},
                {"id":"main","name":"sketch.js","fileType":"file","content":"let image; function preload(){ image=loadImage('assets/picture.png'); }"},
                {"id":"index","name":"index.html","fileType":"file","content":"<script src='https://cdn.jsdelivr.net/npm/p5@2.3.3/lib/p5.min.js'></script><script src='p5.sound.min.js'></script>"},
                {"id":"helper","name":"helper.js","fileType":"file","content":"const answer = 42;"},
                {"id":"assets","name":"assets","fileType":"folder","children":["picture"]},
                {"id":"picture","name":"picture.png","fileType":"file","content":"fake image"}
              ]
            }]}
        """.trimIndent()

        val sketch = parseP5Sketches(json).single()
        assertEquals(listOf("sketch.js", "helper.js", "assets/picture.png", "index.html"), sketch.files.map { it.path })

        val directory = Files.createTempDirectory("p5-import-test").toFile()
        try {
            val imported = importP5Sketch(sketch, AssetStorage(directory))
            assertEquals("Imported drawing", imported.name)
            assertTrue(imported.code.contains("assets/picture.png"))
            assertEquals("const answer = 42;", imported.supportingFiles["helper.js"])
            assertTrue(imported.assets.containsKey("assets/picture.png"))
            assertEquals(sketch.files.first { it.path == "index.html" }.content, imported.supportingFiles["index.html"])
            assertEquals(P5_VERSION_CURRENT, imported.p5Version)
            assertTrue(imported.p5SoundEnabled)
            assertTrue(AssetStorage(directory).contains(imported.assets.getValue("assets/picture.png")))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun usernameValidationRejectsPathsAndUrls() {
        assertTrue(validP5Username("creative-coder_5"))
        assertFalse(validP5Username("../account"))
        assertFalse(validP5Username("https://editor.p5js.org"))
    }

    @Test fun htmlModuleCssAndFolderReferencesImportWithoutRenamingOrSourceRewrites() {
        val html = "<html><head><link rel='stylesheet' href='../styles/style.css'><script type='module' src='../src/app.mjs'></script></head></html>"
        val module = "import { paint } from './helpers/paint.mjs'; const label = 'assets/image.png'; paint();"
        val sketch = P5Sketch("module", "Module page", listOf(
            P5SketchFile("pages/index.html", html, null), P5SketchFile("styles/style.css", "canvas { width: 50%; }", null),
            P5SketchFile("src/app.mjs", module, null), P5SketchFile("src/helpers/paint.mjs", "export function paint() {}", null),
            P5SketchFile("data/settings.json", "{\"size\":4}", null), P5SketchFile("notes/custom.extension", "a text file", null),
            P5SketchFile("assets/image.png", "image bytes", null)))
        val directory = Files.createTempDirectory("p5-module-import").toFile()
        try {
            val imported = importP5Sketch(sketch, AssetStorage(directory))
            assertEquals("", imported.code)
            assertEquals(html, imported.supportingFiles["pages/index.html"])
            assertEquals(module, imported.supportingFiles["src/app.mjs"])
            assertEquals("canvas { width: 50%; }", imported.supportingFiles["styles/style.css"])
            assertEquals("a text file", imported.supportingFiles["notes/custom.extension"])
            assertTrue(imported.assets.containsKey("assets/image.png"))
            assertEquals("pages/index.html", projectEntryDocument(imported.supportingFiles, readProjectDocumentConfig(imported.supportingFiles)))
        } finally { directory.deleteRecursively() }
    }

    @Test fun htmlOnlySketchAndArbitraryP5VersionAreRetained() {
        val html = "<script src='https://cdn.jsdelivr.net/npm/p5@1.9.4/lib/p5.min.js'></script><p>Custom page</p>"
        val directory = Files.createTempDirectory("p5-html-import").toFile()
        try {
            val imported = importP5Sketch(P5Sketch("html", "Page", listOf(P5SketchFile("index.html", html, null))), AssetStorage(directory))
            assertEquals(html, imported.supportingFiles["index.html"])
            assertTrue(prepareProjectHtml(html, "token").contains("p5@1.9.4"))
            assertEquals("", imported.code)
        } finally { directory.deleteRecursively() }
    }

    @Test fun unsafeAndDuplicatePathsRejectBeforePublishingAssets() {
        val directory = Files.createTempDirectory("p5-invalid-import").toFile()
        try {
            for (paths in listOf(listOf("../sketch.js"), listOf("sketch.js", "sketch.js"))) {
                val sketch = P5Sketch("bad", "Bad", paths.map { P5SketchFile(it, "function setup() {}", null) })
                assertTrue(runCatching { importP5Sketch(sketch, AssetStorage(directory)) }.isFailure)
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun cumulativeUtf8TextLimitRejectsBeforeBinaryAssetsArePublished() {
        val text = "あ".repeat((MAX_PROJECT_TEXT_BYTES / 6).toInt() + 1)
        val sketch = P5Sketch("large", "Large", listOf(P5SketchFile("sketch.js", text, null),
            P5SketchFile("data/notes.txt", text, null), P5SketchFile("assets/picture.png", "binary", null)))
        val directory = Files.createTempDirectory("p5-text-limit").toFile()
        try {
            val result = runCatching { importP5Sketch(sketch, AssetStorage(directory)) }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("16 MiB"))
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally { directory.deleteRecursively() }
    }
}
