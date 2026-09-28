package com.hikariatelier.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hikariatelier.app.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private var sessionReady by mutableStateOf(false)
    private val sessionViewModel: EditorSessionViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()
    private val searchReplaceViewModel: SearchReplaceViewModel by viewModels()
    private val consoleViewModel: ConsoleViewModel by viewModels()
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val settingsViewModel: SettingsViewModel by viewModels {
        viewModelFactory { initializer { SettingsViewModel(settingsRepository) } }
    }
    private val recordingViewModel: RecordingViewModel by viewModels {
        viewModelFactory { initializer { RecordingViewModel(PreviewMediaRepository(applicationContext)) } }
    }
    private val workManagementViewModel: WorkManagementViewModel by viewModels {
        viewModelFactory { initializer {
            WorkManagementViewModel(sessionViewModel,
                WorkStoreRepository(applicationContext, assetStorage, sessionViewModel.unreadableFolderUris, "hikari_atelier_works.json"),
                DraftSnapshotRepository(File(filesDir, "editkiro_draft.json")),
                WorkTransferRepository(applicationContext, assetStorage), settingsViewModel,
                WorkFolderRepository(applicationContext, settingsRepository.preferences))
        } }
    }
    private val snapshotViewModel: WorkSnapshotViewModel by viewModels {
        viewModelFactory { initializer { WorkSnapshotViewModel(sessionViewModel, workManagementViewModel, filesDir) } }
    }
    private val assetStorage by lazy { AssetStorage(File(filesDir, "project-assets")) }
    private var preview: PreviewController? = null

    private fun uiText(source: String, vararg arguments: Any?): String {
        val deviceLanguage = ConfigurationCompat.getLocales(resources.configuration)[0]?.language ?: "en"
        val translated = translateUi(source, resolveUiLanguage(settingsViewModel.appLanguage, deviceLanguage))
        return if (arguments.isEmpty()) translated
            else String.format(java.util.Locale.ROOT, translated, *arguments)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewModels are resolved only after super.onCreate attaches the Activity.
        val models = EditorModels(sessionViewModel, updateViewModel, searchReplaceViewModel,
            consoleViewModel, settingsViewModel, recordingViewModel, workManagementViewModel, snapshotViewModel)
        val controller = PreviewController(this, assetStorage, models)
        preview = controller
        val media = PreviewMediaActions(this) { source, arguments -> uiText(source, *arguments) }
        updateViewModel.checkAtStartup()
        enableEdgeToEdge()
        setContent {
            val settings = settingsViewModel.state
            AppTheme(themeMode = settings.themeMode, customBackground = Color(settings.customBackground),
                customAccent = Color(settings.customAccent), customFont = settingsViewModel.customFontFamily,
                ligatures = settings.fontLigatures) {
                UpdateDialog(updateViewModel, ::uiText) { openExternalUrl(RELEASES_URL) }
                if (sessionReady) {
                    EditorScreen(models, controller, media, ::openExternalUrl)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
        lifecycleScope.launch {
            settingsViewModel.loadFont(applicationContext)
            workManagementViewModel.initialize { defaultWorks(assets) }
            sessionReady = true
        }
    }

    private fun openExternalUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, uiText("ブラウザーを開けませんでした"), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        workManagementViewModel.saveDraft()
        preview?.pause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.preferHighRefreshRate()
    }

    override fun onResume() {
        super.onResume()
        preview?.resume()
    }

    override fun onDestroy() {
        preview?.close()
        preview = null
        super.onDestroy()
    }
}
