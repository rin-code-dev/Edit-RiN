package com.hikariatelier.app

import android.content.SharedPreferences
import com.hikariatelier.app.ui.theme.AppThemeMode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SettingsRepositoryTest {
    private class Preferences : SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?) = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = values[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun contains(key: String?) = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private var clear = false
            private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = value }
            override fun putString(key: String?, value: String?) = put(key, value)
            override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values)
            override fun putInt(key: String?, value: Int) = put(key, value)
            override fun putLong(key: String?, value: Long) = put(key, value)
            override fun putFloat(key: String?, value: Float) = put(key, value)
            override fun putBoolean(key: String?, value: Boolean) = put(key, value)
            override fun remove(key: String?) = put(key, null)
            override fun clear(): SharedPreferences.Editor = apply { clear = true }
            override fun commit(): Boolean {
                if (clear) values.clear()
                changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return true
            }
            override fun apply() { commit() }
        }
    }
    @Test fun authorNameIsLoadedFromTheExistingPreferenceKey() {
        val prefs = Preferences().apply { values["setting_share_card_author"] = "RiN" }
        val vm = SettingsViewModel(SettingsRepository(prefs))
        assertEquals("RiN", vm.shareCardAuthor)
        vm.shareCardAuthor = "New author"
        assertEquals("New author", SettingsViewModel(SettingsRepository(prefs)).shareCardAuthor)
    }
    @Test fun settingsUseExistingKeysAndLeaveFolderSelectionAlone() {
        val prefs = Preferences().apply {
            values["works_folder_uri"] = "keep-folder"
            values["dismissed_sample_prompt_version"] = 21
        }
        val repository = SettingsRepository(prefs)
        val state = SettingsUiState(themeMode = AppThemeMode.CUSTOM, editorWordWrap = false,
            landscapePreviewFraction = 0.65f, mp4BitrateMbps = 10, recordingCountdownSeconds = 5,
            shareCardAuthor = "Author", appLanguage = "en", fontLigatures = true)
        repository.save(state)
        assertEquals(state, repository.load())
        assertEquals(0.65f, prefs.values["setting_landscape_preview_split_v2"])
        assertEquals("keep-folder", prefs.values["works_folder_uri"])
        assertEquals(21, prefs.values["dismissed_sample_prompt_version"])
    }
    @Test fun backupRoundTripPreservesSettingsAndExistingKeyNames() {
        val original = SettingsUiState(themeMode = AppThemeMode.LIGHT, appLanguage = "zh",
            showStatusBar = false, manualRotation = false, landscapeEditorOnLeft = false,
            editorWordWrap = false, mp4BitrateMbps = 2, recordingCountdownSeconds = 0,
            shareCardAuthor = "Author", landscapePreviewFraction = 0.35f)
        val json = JSONObject(original.toBackupJson())
        assertTrue(json.has("landscapePreviewSplit")); assertTrue(json.has("accessoryBar"))
        assertTrue(json.has("resizeHandlesVisible")); assertTrue(json.has("lineNumbers"))
        assertEquals(original, SettingsUiState().restoredFromBackup(json))
    }
    @Test fun legacyBackupKeepsSettingsThatWereNotExportedBefore() {
        val existing = SettingsUiState(editorWordWrap = false, shareCardAuthor = "Keep", manualRotation = false,
            customFontFile = "imported-old.font", importedFontName = "Installed font")
        val restored = existing.restoredFromBackup(JSONObject().put("autoRun", false))
        assertFalse(restored.autoRun); assertFalse(restored.editorWordWrap)
        assertFalse(restored.manualRotation); assertEquals("Keep", restored.shareCardAuthor)
        assertEquals("imported-old.font", restored.customFontFile)
        assertEquals("Installed font", restored.importedFontName)
    }
    @Test fun newInstallDefaultsToHiddenBars() {
        val state = SettingsRepository(Preferences()).load()
        assertFalse(state.showStatusBar)
        assertFalse(state.showNavigationBar)
    }
    @Test fun systemBarsPersistIndependently() {
        val repository = SettingsRepository(Preferences())
        for (status in listOf(false, true)) for (navigation in listOf(false, true)) {
            val state = SettingsUiState(showStatusBar = status, showNavigationBar = navigation)
            repository.save(state)
            assertEquals(state, repository.load())
            assertEquals(state, SettingsUiState().restoredFromBackup(JSONObject(state.toBackupJson())))
        }
    }
    @Test fun oldCombinedBarSettingMigratesToBothBars() {
        val prefs = Preferences().apply { values["setting_status_bar"] = false }
        val repository = SettingsRepository(prefs)
        assertFalse(repository.load().showNavigationBar)
        val restored = SettingsUiState().restoredFromBackup(JSONObject().put("showStatusBar", false))
        assertFalse(restored.showStatusBar)
        assertFalse(restored.showNavigationBar)
    }

    @Test fun invalidBackupValuesAreNormalized() {
        val json = JSONObject().put("editorFontSize", 500).put("landscapePreviewSplit", 0.59)
            .put("mp4BitrateMbps", 123).put("recordingCountdownSeconds", -1)
            .put("appLanguage", "unsupported").put("p5Username", "invalid/name")
            .put("xShareText", "x".repeat(1200))
        val restored = SettingsUiState().restoredFromBackup(json)
        assertEquals(28f, restored.editorFontSize)
        assertEquals(0.65f, restored.landscapePreviewFraction)
        assertEquals(5, restored.mp4BitrateMbps); assertEquals(3, restored.recordingCountdownSeconds)
        assertEquals("system", restored.appLanguage); assertEquals("", restored.p5Username)
        assertEquals(1000, restored.xShareText.length)
    }
    @Test fun consecutiveUpdatesUseTheLatestState() {
        val vm = SettingsViewModel(SettingsRepository(Preferences()))
        vm.update { copy(editorWordWrap = false) }
        vm.update { copy(autoRun = false) }
        assertFalse(vm.editorWordWrap); assertFalse(vm.autoRun)
    }
    @Test fun disablingDraftRecoveryNotifiesTheDraftOwner() {
        val vm = SettingsViewModel(SettingsRepository(Preferences()))
        var calls = 0
        vm.onDraftRecoveryDisabled = { calls++ }
        vm.update { copy(draftRecovery = false) }
        vm.update { copy(autoRun = false) }
        assertEquals(1, calls)
    }
    @Test fun jsExportNameSanitizesBothSlashDirections() {
        assertEquals("a_b_c.js", safeJsFileName("a/b\\c"))
        assertEquals("sketch.js", safeJsFileName("  "))
        assertEquals("existing.JS", safeJsFileName("existing.JS"))
    }
}
