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
            assertTrue(imported.assets.containsKey("picture.png"))
            assertEquals(P5_VERSION_CURRENT, imported.p5Version)
            assertTrue(imported.p5SoundEnabled)
            assertTrue(AssetStorage(directory).contains(imported.assets.getValue("picture.png")))
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
}
