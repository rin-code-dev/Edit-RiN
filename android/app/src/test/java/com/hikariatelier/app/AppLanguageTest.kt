package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class AppLanguageTest {
    @Test fun explicitLanguageOverridesDevice() {
        for (language in listOf("ja", "en", "zh")) {
            assertEquals(language, resolveUiLanguage(language, "fr"))
        }
    }
    @Test fun systemRecognizesSupportedLanguagesAndChineseRegions() {
        assertEquals("ja", resolveUiLanguage("system", "ja"))
        assertEquals("en", resolveUiLanguage("system", "en-US"))
        assertEquals("zh", resolveUiLanguage("system", "zh-TW"))
        assertEquals("zh", resolveUiLanguage("system", "zh_CN"))
    }
    @Test fun unsupportedLanguagesUseEnglish() {
        for (language in listOf("fr", "ko", "de", "", "ar")) {
            assertEquals("en", resolveUiLanguage("system", language))
        }
    }
    @Test fun allTranslationsExistAndJapaneseIsUnchanged() {
        assertTrue(uiTranslations.size >= 190)
        uiTranslations.forEach { (ja, translations) ->
            assertTrue(translations.first.isNotBlank())
            assertTrue(translations.second.isNotBlank())
            assertEquals(ja, translateUi(ja, "ja"))
            assertEquals(translations.first, translateUi(ja, "en"))
            assertEquals(translations.second, translateUi(ja, "zh"))
        }
    }
    @Test fun userContentIsNotTranslated() {
        assertEquals("mySketch.js", translateUi("mySketch.js", "zh"))
    }
}
