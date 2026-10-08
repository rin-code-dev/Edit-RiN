package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class UserGuideTest {
    @Test fun translationsCoverTheSameTasksAndWorkingParameterExample() {
        val english = localizedUserGuide("en")
        for (language in listOf("ja", "en", "zh")) {
            val guide = localizedUserGuide(language)
            assertEquals(english.size, guide.size)
            assertEquals(guide.size, guide.map { it.title }.distinct().size)
            guide.zip(english).forEach { (section, source) ->
                assertTrue(section.title.isNotBlank())
                assertTrue(section.summary.isNotBlank())
                assertTrue(section.steps.all { it.isNotBlank() })
                assertEquals(source.steps.size, section.steps.size)
                assertEquals(source.iconRes, section.iconRes)
                assertEquals(source.codeSnippet, section.codeSnippet)
            }
            val code = guide.mapNotNull { it.codeSnippet }.single()
            val parameters = workParameters(mapOf("sketch.js" to code))
            assertEquals(setOf("diameter", "ink"), parameters.map { it.name }.toSet())
            assertTrue(parameters.all { code.contains("rinParams.${it.name}") })
            assertEquals("120.0", parameters.first { it.name == "diameter" }.defaultValue)
            assertEquals("#D67856", parameters.first { it.name == "ink" }.defaultValue)
        }
        assertEquals(english, localizedUserGuide("unsupported"))
    }

    @Test fun guideOmitsPausedFeatureAndOptionalThemeDetails() {
        for (language in listOf("ja", "en", "zh")) {
            val text = localizedUserGuide(language).joinToString("\n") {
                listOf(it.title, it.summary, it.tag, it.steps.joinToString("\n")).joinToString("\n")
            }
            listOf("share card", "シェアカード", "分享卡片", "Sumi", "コメントの言語", "comment language").forEach {
                assertFalse("Guide exposes $it in $language", text.contains(it, ignoreCase = true))
            }
        }
    }
}
