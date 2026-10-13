package com.hikariatelier.app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SampleCodeLocalizationTest {
    @Test fun allBundledCommentsHaveTranslationsWithoutChangingExecutableLines() {
        val samples = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, "www/samples") }.first { it.isDirectory }
        val names = listOf("Halo", "Shapes", "Touch", "Gravity", "wave Parameter", "WebGPU",
            "Sound", "Camera", "Microphone", "Sensor")
        for (name in names) {
            val source = File(samples, "$name.js").readText()
            assertEquals(source, localizedSampleCode(source, "en"))
            for (language in listOf("ja", "zh")) {
                val localized = localizedSampleCode(source, language)
                val originalLines = source.split('\n')
                val localizedLines = localized.split('\n')
                assertEquals("$name line numbers", originalLines.size, localizedLines.size)
                for ((index, line) in originalLines.withIndex()) {
                    val comment = line.trimStart()
                    if (comment.startsWith("// @rin")) {
                        val original = workParameters(mapOf("sketch.js" to line)).single()
                        val translated = workParameters(mapOf("sketch.js" to localizedLines[index])).single()
                        val normalized = when (translated) {
                            is WorkParameter.Number -> translated.copy(label = original.label)
                            is WorkParameter.Color -> translated.copy(label = original.label)
                            is WorkParameter.Boolean -> translated.copy(label = original.label)
                            is WorkParameter.Choice -> translated.copy(label = original.label)
                        }
                        assertEquals("$name parameter contract $index", original, normalized)
                        if (name == "wave Parameter" || name == "WebGPU") {
                            assertNotEquals(original.label, translated.label)
                        }
                    } else if (comment.startsWith("// ")) {
                        assertNotEquals("$name $language comment: $comment", line, localizedLines[index])
                    } else {
                        assertEquals("$name executable/metadata line $index", line, localizedLines[index])
                    }
                }
            }
        }
    }

    @Test fun unknownCommentsAndCommentLikeStringsStayUntouched() {
        val source = "// Personal note\nconst text = '// Tap to change the palette.';\n"
        assertEquals(source, localizedSampleCode(source, "ja"))
    }
}
