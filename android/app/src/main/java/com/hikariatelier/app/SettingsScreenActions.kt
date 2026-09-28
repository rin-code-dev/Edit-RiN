package com.hikariatelier.app

import androidx.compose.ui.text.font.FontFamily

internal data class FontSettingsUiState(val family: FontFamily?, val name: String, val busy: Boolean)
internal data class SettingsScreenActions(
    val onBack: () -> Unit,
    val onChooseFolder: () -> Unit,
    val onImportOfficialSamples: () -> Unit,
    val onImportFont: () -> Unit,
    val onResetFont: () -> Unit,
    val onImportP5: () -> Unit,
    val onImportJs: () -> Unit,
    val onExportJs: () -> Unit,
    val onExportBackup: () -> Unit,
    val onImportBackup: () -> Unit,
    val openExternalUrl: (String) -> Unit,
)
