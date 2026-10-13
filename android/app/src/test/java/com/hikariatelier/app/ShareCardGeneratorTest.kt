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
    fun shareCardThemesMatchExpectedIdentifiersAndValidHexColors() {
        val hexRegex = Regex("^#[0-9A-Fa-f]{6}$")
        assertEquals(3, ShareCardTheme.entries.size)

        for (theme in ShareCardTheme.entries) {
            assertTrue(theme.id in listOf("dark", "light", "sumi"))
            assertTrue(hexRegex.matches(theme.bgColor))
            assertTrue(hexRegex.matches(theme.artBorderColor))
            assertTrue(hexRegex.matches(theme.titleColor))
            assertTrue(hexRegex.matches(theme.subtitleColor))
            assertTrue(hexRegex.matches(theme.dividerColor))
            assertTrue(hexRegex.matches(theme.codeBoxBg))
            assertTrue(hexRegex.matches(theme.codeBoxBorder))
            assertTrue(hexRegex.matches(theme.codeTextColor))
            assertTrue(hexRegex.matches(theme.codeLineNumColor))
            assertTrue(hexRegex.matches(theme.urlColor))
        }
    }

    @Test
    fun renderShareCardProducesValidBitmapAcrossAllLayouts() {
        val dummyArtwork = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888)

        // Layout A: Code + QR with long URL
        val configA = ShareCardConfig(
            title = "A".repeat(80),
            author = "TestAuthorWithALongHandle",
            fullCode = "function setup() { createCanvas(400, 400); }\nfunction draw() { background(0); }",
            snippetCode = "function setup() { createCanvas(400, 400); }",
            includeCode = true,
            includeQr = true,
            qrUrl = "https://example.com/very/long/path/with/lots/of/parameters?foo=bar&baz=qux"
        )
        val cardA = ShareCardGenerator.renderShareCard(dummyArtwork, configA)
        assertNotNull(cardA)
        assertEquals(1920, cardA.width)
        assertEquals(1080, cardA.height)

        // Layout B: Code only
        val configB = configA.copy(includeQr = false)
        val cardB = ShareCardGenerator.renderShareCard(dummyArtwork, configB)
        assertNotNull(cardB)

        // Layout C: QR only
        val configC = configA.copy(includeCode = false)
        val cardC = ShareCardGenerator.renderShareCard(dummyArtwork, configC)
        assertNotNull(cardC)

        // Layout D: Minimal
        val configD = configA.copy(includeCode = false, includeQr = false)
        val cardD = ShareCardGenerator.renderShareCard(dummyArtwork, configD)
        assertNotNull(cardD)

        // Test LIGHT and SUMI themes
        val lightCard = ShareCardGenerator.renderShareCard(dummyArtwork, configA.copy(theme = ShareCardTheme.LIGHT))
        assertNotNull(lightCard)
        val sumiCard = ShareCardGenerator.renderShareCard(dummyArtwork, configA.copy(theme = ShareCardTheme.SUMI))
        assertNotNull(sumiCard)
    }
}
