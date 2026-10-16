package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {
    @Test fun comparesBetaNumbersNumerically() {
        assertTrue(ReleaseVersion.parse("v1.0.0-beta.10")!! > ReleaseVersion.parse("1.0.0-beta.3")!!)
    }
    @Test fun stableFollowsBetaAndMetadataIsIgnored() {
        assertTrue(ReleaseVersion.parse("1.0.0")!! > ReleaseVersion.parse("1.0.0-beta.99")!!)
        assertEquals(0, ReleaseVersion.parse("1.0.0+build1")!!.compareTo(ReleaseVersion.parse("v1.0.0")!!))
        assertTrue(ReleaseVersion.parse("1.1.0-beta.1")!! > ReleaseVersion.parse("1.0.9")!!)
    }
    @Test fun rejectsUnknownTags() {
        listOf("", "latest", "1", "1.0.0-beta..3").forEach { assertNull(ReleaseVersion.parse(it)) }
    }
    @Test fun selectsStableReleasesOnlyWithoutTrustingOrder() {
        val json = """[
            {"tag_name":"v1.0.0-beta.3","prerelease":true},
            {"tag_name":"v2.0.0","draft":true},
            {"tag_name":"v1.0.0-beta.10","prerelease":true},
            {"tag_name":"v1.0.0"},
            {"tag_name":"v1.1.0-beta.1"},
            {"tag_name":"latest"}
        ]"""
        assertEquals("v1.0.0", newestRelease(json)?.tag)
        assertNull(newestRelease("[]"))
    }

    @Test fun displayVersionMatchesCanonicalTag() {
        assertEquals(ReleaseVersion.parse("v1.0.0"), ReleaseVersion.parse("1.00"))
        assertTrue(ReleaseVersion.parse("1.00")!! > ReleaseVersion.parse("1.0.0-beta.4")!!)
    }
    @Test fun startupIsSilentExceptForNewerStableRelease() {
        val same = AppRelease("v1.0.0", ReleaseVersion.parse("v1.0.0")!!)
        val newer = AppRelease("v1.0.1", ReleaseVersion.parse("v1.0.1")!!)
        assertNull(evaluateUpdate(null, "1.00", true).message)
        assertNull(evaluateUpdate(same, "1.00", true).message)
        assertNull(evaluateUpdate(same, "1.0.1", true).message)
        assertNull(evaluateUpdate(newer, "invalid", true).message)
        assertEquals(newer, evaluateUpdate(newer, "1.00", true).release)
        assertNotNull(evaluateUpdate(newer, "1.00", true).message)
        assertNull(updateFailureMessage(true))
        assertNotNull(updateFailureMessage(false))
        assertNotNull(evaluateUpdate(same, "1.00", false).message)
    }

    @Test fun parsesReleaseHighlightsFromMarkdownBody() {
        val body = """
            ## What's New in v3.2.0
            Here is the update summary:
            - **Live Parameters**: Declare sliders via comments
            - #つぶやきProcessing: Smart minify and HUD gauge
            * PiP Ghosting: Transparent background editing
            - Excess Item: Should not exceed 3
        """.trimIndent()
        val highlights = parseReleaseHighlights(body)
        assertEquals(3, highlights.size)
        assertEquals("Live Parameters", highlights[0].title)
        assertEquals("Declare sliders via comments", highlights[0].description)
        assertEquals("#つぶやきProcessing", highlights[1].title)
        assertEquals("Smart minify and HUD gauge", highlights[1].description)
        assertEquals("PiP Ghosting", highlights[2].title)
        assertEquals("Transparent background editing", highlights[2].description)

        assertTrue(parseReleaseHighlights("").isEmpty())
        assertTrue(parseReleaseHighlights("Just random text without bullets").isEmpty())

        val multilingualBody = """
            ### English
            - Live Parameters: Sliders in preview
            - Tweet Coding: Minify tool
            - PiP Mode: Transparent window

            ### 日本語
            - コメント駆動パラメータ: プレビュー連動スライダー
            - #つぶやきProcessing: コード圧縮機能
            - PiP小窓: 半透明化とタッチ透過
        """.trimIndent()
        val jaHighlights = parseReleaseHighlights(multilingualBody, "ja")
        assertEquals(3, jaHighlights.size)
        assertEquals("コメント駆動パラメータ", jaHighlights[0].title)
        assertEquals("プレビュー連動スライダー", jaHighlights[0].description)
        assertEquals("#つぶやきProcessing", jaHighlights[1].title)

        val enHighlights = parseReleaseHighlights(multilingualBody, "en")
        assertEquals(3, enHighlights.size)
        assertEquals("Live Parameters", enHighlights[0].title)
    }
    @Test fun languageSectionsUseExactNamesAndKeepNestedHeadings() {
        val body = """
            ## Changes
            - Wrong: Not a language section
            ## English
            ### Features
            - First: One
            ### Fixes
            - Second: Two
            ## 日本語
            - 日本語項目: 説明
        """.trimIndent()
        assertEquals(listOf("First", "Second"), parseReleaseHighlights(body, "en").map { it.title })
        assertEquals("日本語項目", parseReleaseHighlights(body, "ja").single().title)
        assertEquals(listOf("First", "Second"), parseReleaseHighlights(body, "zh").map { it.title })
    }

    @Test fun codeExamplesAndRulesAreNotHighlights() {
        val body = "## English\n```md\n## 日本語\n- Example: Ignore\n```\n---\n- Actual: Show"
        assertEquals("Actual", parseReleaseHighlights(body, "en").single().title)
    }
}
