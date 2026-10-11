package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareCardGeneratorTest {

    @Test
    fun checkQrUrlStatusAcceptsStandardUrls() {
        assertEquals(QrStatus.AVAILABLE, ShareCardGenerator.checkQrUrlStatus(DEFAULT_SHARE_URL))
        assertEquals(QrStatus.AVAILABLE, ShareCardGenerator.checkQrUrlStatus("https://example.com/p5/sketch?id=123"))
        assertEquals(QrStatus.AVAILABLE, ShareCardGenerator.checkQrUrlStatus("https://p5js.org"))
    }

    @Test
    fun checkQrUrlStatusRejectsEmptyAndBlankUrls() {
        assertEquals(QrStatus.TOO_LARGE, ShareCardGenerator.checkQrUrlStatus(""))
        assertEquals(QrStatus.TOO_LARGE, ShareCardGenerator.checkQrUrlStatus("   "))
    }

    @Test
    fun checkQrUrlStatusRejectsExcessivelyLongUrls() {
        val longUrl = "https://example.com/" + "a".repeat(4000)
        assertEquals(QrStatus.TOO_LARGE, ShareCardGenerator.checkQrUrlStatus(longUrl))
    }

    @Test
    fun cleanCodeForQrPreservesRinAnnotationsAndStripsComments() {
        val code = """
            // This is a comment that should be stripped
            // @rin title="Interactive Art"
            function setup() {
                createCanvas(400, 400);
            }
            //# sourceURL=sketch.js
        """.trimIndent()

        val cleaned = ShareCardGenerator.cleanCodeForQr(code)
        assertTrue(cleaned.contains("// @rin title=\"Interactive Art\""))
        assertFalse(cleaned.contains("This is a comment that should be stripped"))
        assertFalse(cleaned.contains("sourceURL"))
        assertTrue(cleaned.contains("function setup() {"))
    }

    @Test
    fun buildWebPlayerUrlGeneratesValidUrlWithExpectedPrefix() {
        val code = "function setup() { createCanvas(100, 100); background(0); }"
        val viewerUrl = ShareCardGenerator.buildWebPlayerUrl(code, allowWebCodeView = true)

        assertTrue(viewerUrl.startsWith(ShareCardGenerator.WEB_VIEWER_BASE_URL))
        assertFalse(viewerUrl.endsWith("&src=0"))

        val noSourceUrl = ShareCardGenerator.buildWebPlayerUrl(code, allowWebCodeView = false)
        assertTrue(noSourceUrl.endsWith("&src=0"))
    }

    @Test
    fun checkQrStatusHandlesAssetsAndCapacity() {
        val smallCode = "function setup() { createCanvas(100, 100); }"
        assertEquals(QrStatus.CONTAINS_ASSETS, ShareCardGenerator.checkQrStatus(smallCode, hasAssets = true))
        assertEquals(QrStatus.AVAILABLE, ShareCardGenerator.checkQrStatus(smallCode, hasAssets = false))

        val hugeCode = "function setup() {\n" + "let x = 1;\n".repeat(2000) + "}"
        assertEquals(QrStatus.TOO_LARGE, ShareCardGenerator.checkQrStatus(hugeCode, hasAssets = false))
    }
}
