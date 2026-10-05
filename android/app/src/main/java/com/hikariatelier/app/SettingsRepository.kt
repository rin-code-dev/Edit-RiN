package com.hikariatelier.app

import com.hikariatelier.app.ui.theme.AppThemeMode
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Owns preference keys and the existing backup settings format. */
internal interface SettingsPersistence {
    val samplePromptVersion: Int get() = 0
    fun dismissSamplePrompt(version: Int) {}
    val releaseNotesPromptVersion: Int get() = 0
    fun dismissReleaseNotesPrompt(version: Int) {}
    fun load(): SettingsUiState
    fun save(state: SettingsUiState)
}

internal class SettingsRepository(val preferences: SharedPreferences) : SettingsPersistence {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("ugoku_atelier_prefs", Context.MODE_PRIVATE))

    override val samplePromptVersion get() = preferences.getInt("dismissed_sample_prompt_version", 0)
    override fun dismissSamplePrompt(version: Int) {
        preferences.edit().putInt("dismissed_sample_prompt_version", version).apply()
    }

    override val releaseNotesPromptVersion get() = preferences.getInt("dismissed_release_notes_version", 0)
    override fun dismissReleaseNotesPrompt(version: Int) {
        preferences.edit().putInt("dismissed_release_notes_version", version).apply()
    }

    override fun load(): SettingsUiState = SettingsUiState(
        themeMode = runCatching { AppThemeMode.valueOf(preferences.getString("theme_mode", AppThemeMode.DARK.name).orEmpty()) }.getOrDefault(AppThemeMode.DARK),
        appLanguage = preferences.getString("setting_app_language", "system").orEmpty().takeIf { it in listOf("system", "ja", "en", "zh") } ?: "system",
        customBackground = preferences.getInt("custom_background", 0xFF101014.toInt()),
        customAccent = preferences.getInt("custom_accent", 0xFFA8C7FA.toInt()),
        showStatusBar = preferences.getBoolean("setting_status_bar", false),
        showNavigationBar = preferences.getBoolean("setting_navigation_bar", preferences.getBoolean("setting_status_bar", false)),
        landscapeUseCutout = preferences.getBoolean("setting_landscape_use_cutout", false),
        manualRotation = preferences.getBoolean("setting_manual_rotation", true),
        autoRun = preferences.getBoolean("setting_auto_run", true),
        hideEditingPreview = preferences.getBoolean("hide_editing_preview", true),
        compactPreview = preferences.getBoolean("setting_compact_preview", true),
        preserveExpandedPreview = preferences.getBoolean("setting_preserve_expanded_preview", true),
        landscapePreviewFraction = normalizedLandscapeSplit(preferences.getFloat("setting_landscape_preview_split_v2", 0.5f)),
        showResizeHandles = preferences.getBoolean("setting_resize_handles_visible", true),
        editorFontSize = preferences.getFloat("setting_editor_font", 14f).takeIf { it.isFinite() }?.coerceIn(12f, 20f) ?: 14f,
        autoIndent = preferences.getBoolean("setting_auto_indent", true),
        showLineNumbers = preferences.getBoolean("setting_line_numbers", true),
        showEditorAccessoryBar = preferences.getBoolean("setting_editor_accessory_bar", true),
        codeCompletion = preferences.getBoolean("setting_code_completion", true),
        showAccessoryNavigation = preferences.getBoolean("setting_accessory_navigation", true),
        showAccessorySymbols = preferences.getBoolean("setting_accessory_symbols", true),
        compactAccessoryKeys = preferences.getBoolean("setting_compact_accessory_keys", true),
        editorWordWrap = preferences.getBoolean("setting_editor_word_wrap", true),
        landscapeEditorOnLeft = preferences.getBoolean("setting_landscape_editor_on_left", true),
        draftRecovery = preferences.getBoolean("setting_draft_recovery", true),
        mp4BitrateMbps = preferences.getInt("setting_mp4_bitrate_mbps", 5).takeIf(MP4_BITRATE_OPTIONS::contains) ?: 5,
        xShareText = preferences.getString("setting_x_share_text", DEFAULT_X_SHARE_TEXT).orEmpty(),
        recordingCountdownSeconds = preferences.getInt("setting_recording_countdown_seconds", 3).takeIf(RECORDING_COUNTDOWN_OPTIONS::contains) ?: 3,
        shareCardAuthor = preferences.getString("setting_share_card_author", "").orEmpty(),
        p5Username = preferences.getString("p5_web_editor_username", "").orEmpty(),
        fontLigatures = preferences.getBoolean("font_ligatures", false),
        customFontFile = preferences.getString("custom_font_file", "").orEmpty(),
        importedFontName = preferences.getString("custom_font_name", "").orEmpty(),
        workSort = preferences.getString("work_sort", "更新順").orEmpty(),
    )

    override fun save(state: SettingsUiState) {
        preferences.edit()
            .putString("theme_mode", state.themeMode.name)
            .putString("setting_app_language", state.appLanguage)
            .putInt("custom_background", state.customBackground)
            .putInt("custom_accent", state.customAccent)
            .putBoolean("setting_status_bar", state.showStatusBar)
            .putBoolean("setting_navigation_bar", state.showNavigationBar)
            .putBoolean("setting_landscape_use_cutout", state.landscapeUseCutout)
            .putBoolean("setting_manual_rotation", state.manualRotation)
            .putBoolean("setting_auto_run", state.autoRun)
            .putBoolean("hide_editing_preview", state.hideEditingPreview)
            .putBoolean("setting_compact_preview", state.compactPreview)
            .putBoolean("setting_preserve_expanded_preview", state.preserveExpandedPreview)
            .putFloat("setting_landscape_preview_split_v2", state.landscapePreviewFraction)
            .putBoolean("setting_resize_handles_visible", state.showResizeHandles)
            .putFloat("setting_editor_font", state.editorFontSize)
            .putBoolean("setting_auto_indent", state.autoIndent)
            .putBoolean("setting_line_numbers", state.showLineNumbers)
            .putBoolean("setting_editor_accessory_bar", state.showEditorAccessoryBar)
            .putBoolean("setting_code_completion", state.codeCompletion)
            .putBoolean("setting_accessory_navigation", state.showAccessoryNavigation)
            .putBoolean("setting_accessory_symbols", state.showAccessorySymbols)
            .putBoolean("setting_compact_accessory_keys", state.compactAccessoryKeys)
            .putBoolean("setting_editor_word_wrap", state.editorWordWrap)
            .putBoolean("setting_landscape_editor_on_left", state.landscapeEditorOnLeft)
            .putBoolean("setting_draft_recovery", state.draftRecovery)
            .putInt("setting_mp4_bitrate_mbps", state.mp4BitrateMbps)
            .putString("setting_x_share_text", state.xShareText)
            .putInt("setting_recording_countdown_seconds", state.recordingCountdownSeconds)
            .putString("setting_share_card_author", state.shareCardAuthor)
            .putString("p5_web_editor_username", state.p5Username)
            .putBoolean("font_ligatures", state.fontLigatures)
            .putString("custom_font_file", state.customFontFile)
            .putString("custom_font_name", state.importedFontName)
            .putString("work_sort", state.workSort)
            .apply()
    }
}

internal fun normalizedLandscapeSplit(value: Float): Float =
    if (!value.isFinite()) 0.5f else LANDSCAPE_PREVIEW_SPLITS.minByOrNull { kotlin.math.abs(it - value) } ?: 0.5f

internal fun SettingsUiState.toBackupJson(): String = JSONObject()
    .put("themeMode", themeMode.name)
    .put("appLanguage", appLanguage)
    .put("customBackground", customBackground)
    .put("customAccent", customAccent)
    .put("showStatusBar", showStatusBar)
    .put("showNavigationBar", showNavigationBar)
    .put("landscapeUseCutout", landscapeUseCutout)
    .put("manualRotation", manualRotation)
    .put("autoRun", autoRun)
    .put("hideEditingPreview", hideEditingPreview)
    .put("compactPreview", compactPreview)
    .put("preserveExpandedPreview", preserveExpandedPreview)
    .put("landscapePreviewSplit", landscapePreviewFraction.toDouble())
    .put("resizeHandlesVisible", showResizeHandles)
    .put("editorFontSize", editorFontSize.toDouble())
    .put("autoIndent", autoIndent)
    .put("lineNumbers", showLineNumbers)
    .put("accessoryBar", showEditorAccessoryBar)
    .put("codeCompletion", codeCompletion)
    .put("accessoryNavigation", showAccessoryNavigation)
    .put("accessorySymbols", showAccessorySymbols)
    .put("compactAccessoryKeys", compactAccessoryKeys)
    .put("editorWordWrap", editorWordWrap)
    .put("landscapeEditorOnLeft", landscapeEditorOnLeft)
    .put("draftRecovery", draftRecovery)
    .put("mp4BitrateMbps", mp4BitrateMbps)
    .put("xShareText", xShareText)
    .put("recordingCountdownSeconds", recordingCountdownSeconds)
    .put("shareCardAuthor", shareCardAuthor)
    .put("p5Username", p5Username)
    .put("fontLigatures", fontLigatures)
    .put("workSort", workSort)
    .toString(2)

internal fun SettingsUiState.restoredFromBackup(json: JSONObject): SettingsUiState = copy(
    themeMode = runCatching { AppThemeMode.valueOf(json.optString("themeMode", themeMode.name)) }.getOrDefault(AppThemeMode.DARK),
    appLanguage = json.optString("appLanguage", appLanguage).takeIf { it in listOf("system", "ja", "en", "zh") } ?: "system",
    customBackground = json.optInt("customBackground", customBackground) or 0xFF000000.toInt(),
    customAccent = json.optInt("customAccent", customAccent) or 0xFF000000.toInt(),
    showStatusBar = json.optBoolean("showStatusBar", showStatusBar),
    showNavigationBar = json.optBoolean("showNavigationBar", json.optBoolean("showStatusBar", showNavigationBar)),
    landscapeUseCutout = json.optBoolean("landscapeUseCutout", landscapeUseCutout),
    manualRotation = json.optBoolean("manualRotation", manualRotation),
    autoRun = json.optBoolean("autoRun", autoRun),
    hideEditingPreview = json.optBoolean("hideEditingPreview", hideEditingPreview),
    compactPreview = json.optBoolean("compactPreview", compactPreview),
    preserveExpandedPreview = json.optBoolean("preserveExpandedPreview", preserveExpandedPreview),
    landscapePreviewFraction = normalizedLandscapeSplit(json.optDouble("landscapePreviewSplit", landscapePreviewFraction.toDouble()).toFloat()),
    showResizeHandles = json.optBoolean("resizeHandlesVisible", showResizeHandles),
    editorFontSize = json.optDouble("editorFontSize", editorFontSize.toDouble()).toFloat().takeIf { it.isFinite() }?.coerceIn(12f, 20f) ?: 14f,
    autoIndent = json.optBoolean("autoIndent", autoIndent),
    showLineNumbers = json.optBoolean("lineNumbers", showLineNumbers),
    showEditorAccessoryBar = json.optBoolean("accessoryBar", showEditorAccessoryBar),
    codeCompletion = json.optBoolean("codeCompletion", codeCompletion),
    showAccessoryNavigation = json.optBoolean("accessoryNavigation", showAccessoryNavigation),
    showAccessorySymbols = json.optBoolean("accessorySymbols", showAccessorySymbols),
    compactAccessoryKeys = json.optBoolean("compactAccessoryKeys", compactAccessoryKeys),
    editorWordWrap = json.optBoolean("editorWordWrap", editorWordWrap),
    landscapeEditorOnLeft = json.optBoolean("landscapeEditorOnLeft", landscapeEditorOnLeft),
    draftRecovery = json.optBoolean("draftRecovery", draftRecovery),
    mp4BitrateMbps = json.optInt("mp4BitrateMbps", mp4BitrateMbps).takeIf(MP4_BITRATE_OPTIONS::contains) ?: 5,
    xShareText = json.optString("xShareText", xShareText).take(1000),
    recordingCountdownSeconds = json.optInt("recordingCountdownSeconds", recordingCountdownSeconds).takeIf(RECORDING_COUNTDOWN_OPTIONS::contains) ?: 3,
    shareCardAuthor = json.optString("shareCardAuthor", shareCardAuthor),
    p5Username = json.optString("p5Username", p5Username).takeIf(::validP5Username).orEmpty(),
    fontLigatures = json.optBoolean("fontLigatures", fontLigatures),
    workSort = json.optString("workSort", workSort),
)
