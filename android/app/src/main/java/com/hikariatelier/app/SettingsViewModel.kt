package com.hikariatelier.app

import com.hikariatelier.app.ui.theme.AppThemeMode
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import java.io.File

internal class SettingsViewModel(private val repository: SettingsPersistence) : ViewModel() {
    var state by mutableStateOf(repository.load())
        private set
    val paletteStartupPending get() = repository.paletteStartupPending
    fun completePaletteStartup() = repository.completePaletteStartup()

    var customFontFamily by mutableStateOf<FontFamily?>(null)
        private set
    var fontImportBusy by mutableStateOf(false)
        private set
    val notices = UiNotices()
    var samplePromptVersion by mutableIntStateOf(repository.samplePromptVersion)
        private set
    fun dismissSamplePrompt(version: Int) {
        repository.dismissSamplePrompt(version)
        samplePromptVersion = version
    }
    var releaseNotesPromptVersion by mutableIntStateOf(repository.releaseNotesPromptVersion)
        private set
    fun dismissReleaseNotesPrompt(version: Int) {
        repository.dismissReleaseNotesPrompt(version)
        releaseNotesPromptVersion = version
    }

    private fun <T> setting(read: (SettingsUiState) -> T, write: (SettingsUiState, T) -> SettingsUiState) =
        object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>) = read(state)
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                state = write(state, value)
                repository.save(state)
            }
        }
    var themeMode by setting({ it.themeMode }, { old, value -> old.copy(themeMode = value) })
    var appLanguage by setting({ it.appLanguage }, { old, value -> old.copy(appLanguage = value) })
    var customBackground by setting({ it.customBackground }, { old, value -> old.copy(customBackground = value) })
    var customAccent by setting({ it.customAccent }, { old, value -> old.copy(customAccent = value) })
    var showStatusBar by setting({ it.showStatusBar }, { old, value -> old.copy(showStatusBar = value) })
    var showNavigationBar by setting({ it.showNavigationBar }, { old, value -> old.copy(showNavigationBar = value) })
    var landscapeUseCutout by setting({ it.landscapeUseCutout }, { old, value -> old.copy(landscapeUseCutout = value) })
    var manualRotation by setting({ it.manualRotation }, { old, value -> old.copy(manualRotation = value) })
    var autoRun by setting({ it.autoRun }, { old, value -> old.copy(autoRun = value) })
    var hideEditingPreview by setting({ it.hideEditingPreview }, { old, value -> old.copy(hideEditingPreview = value) })
    var compactPreview by setting({ it.compactPreview }, { old, value -> old.copy(compactPreview = value) })
    var preserveExpandedPreview by setting({ it.preserveExpandedPreview }, { old, value -> old.copy(preserveExpandedPreview = value) })
    var landscapePreviewFraction by setting({ it.landscapePreviewFraction }, { old, value -> old.copy(landscapePreviewFraction = value) })
    var showResizeHandles by setting({ it.showResizeHandles }, { old, value -> old.copy(showResizeHandles = value) })
    var editorFontSize by setting({ it.editorFontSize }, { old, value -> old.copy(editorFontSize = value) })
    var autoIndent by setting({ it.autoIndent }, { old, value -> old.copy(autoIndent = value) })
    var showLineNumbers by setting({ it.showLineNumbers }, { old, value -> old.copy(showLineNumbers = value) })
    var showEditorAccessoryBar by setting({ it.showEditorAccessoryBar }, { old, value -> old.copy(showEditorAccessoryBar = value) })
    var codeCompletion by setting({ it.codeCompletion }, { old, value -> old.copy(codeCompletion = value) })
    var showAccessoryNavigation by setting({ it.showAccessoryNavigation }, { old, value -> old.copy(showAccessoryNavigation = value) })
    var showAccessorySymbols by setting({ it.showAccessorySymbols }, { old, value -> old.copy(showAccessorySymbols = value) })
    var compactAccessoryKeys by setting({ it.compactAccessoryKeys }, { old, value -> old.copy(compactAccessoryKeys = value) })
    var editorWordWrap by setting({ it.editorWordWrap }, { old, value -> old.copy(editorWordWrap = value) })
    var landscapeEditorOnLeft by setting({ it.landscapeEditorOnLeft }, { old, value -> old.copy(landscapeEditorOnLeft = value) })
    var autoSaveOnLeave by setting({ it.autoSaveOnLeave }, { old, value -> old.copy(autoSaveOnLeave = value) })
    var draftRecovery by setting({ it.draftRecovery }, { old, value -> old.copy(draftRecovery = value) })
    var mp4BitrateMbps by setting({ it.mp4BitrateMbps }, { old, value -> old.copy(mp4BitrateMbps = value) })
    var xShareText by setting({ it.xShareText }, { old, value -> old.copy(xShareText = value) })
    var recordingCountdownSeconds by setting({ it.recordingCountdownSeconds }, { old, value -> old.copy(recordingCountdownSeconds = value) })
    var shareCardAuthor by setting({ it.shareCardAuthor }, { old, value -> old.copy(shareCardAuthor = value) })
    var p5Username by setting({ it.p5Username }, { old, value -> old.copy(p5Username = value) })
    var fontLigatures by setting({ it.fontLigatures }, { old, value -> old.copy(fontLigatures = value) })
    var customFontFile by setting({ it.customFontFile }, { old, value -> old.copy(customFontFile = value) })
    var importedFontName by setting({ it.importedFontName }, { old, value -> old.copy(importedFontName = value) })
    var workSort by setting({ it.workSort }, { old, value -> old.copy(workSort = value) })

    fun update(transform: SettingsUiState.() -> SettingsUiState) {
        val previous = state
        state = state.transform()
        repository.save(state)
        if (previous.draftRecovery && !state.draftRecovery) onDraftRecoveryDisabled?.invoke()
    }
    // The session installs a callback to its draft repository, never to an Activity.
    var onDraftRecoveryDisabled: (() -> Unit)? = null

    fun restoreBackup(json: JSONObject) {
        state = state.restoredFromBackup(json)
        repository.save(state)
    }

    suspend fun loadFont(context: Context) {
        if (fontImportBusy) return
        val folder = File(context.applicationContext.filesDir, "fonts")
        val fileName = state.customFontFile
        val font = withContext(Dispatchers.IO) { loadImportedFont(folder, fileName.takeIf { it.isNotEmpty() }) }
        if (!fontImportBusy && state.customFontFile == fileName) customFontFamily = font?.let { FontFamily(it) }
    }

    fun resetFont() {
        customFontFamily = null
        state = state.copy(customFontFile = "", importedFontName = "")
        repository.save(state)
    }

    fun importFont(context: Context, uri: Uri) {
        if (fontImportBusy) return
        val app = context.applicationContext
        fontImportBusy = true
        viewModelScope.launch {
            try {
                val (imported, name) = withContext(Dispatchers.IO) {
                    val name = documentDisplayName(app.contentResolver, uri) ?: "Imported font"
                    val stream = app.contentResolver.openInputStream(uri) ?: error("Cannot open font")
                    importFont(File(app.filesDir, "fonts"), stream) to name
                }
                customFontFamily = FontFamily(imported.typeface)
                state = state.copy(customFontFile = imported.fileName, importedFontName = name)
                repository.save(state)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notices.send("フォントを読み込めません。20MB以下のTTF・OTF・TTCを選んでください")
            } finally { fontImportBusy = false }
        }
    }
}
