package com.hikariatelier.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellationException
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
    private var openingInstaller = false
    private val installPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateViewModel.report(if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls())
            "インストールを許可しました。インストールボタンで更新を続けてください"
            else "インストールが許可されていません。許可してからもう一度お試しください")
    }
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
                WorkFolderRepository(applicationContext, settingsRepository.preferences),
                templatePersistence = UserTemplateRepository(filesDir, assetStorage))
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
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSystemBarVisibility provides systemBarVisibility(
                        androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE,
                        settings.showStatusBar, settings.showNavigationBar)
                ) {
                UpdateDialog(updateViewModel, ::uiText, ::installUpdate) { openExternalUrl(RELEASES_URL) }
                if (sessionReady) {
                    EditorScreen(models, controller, media, ::openExternalUrl)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                }
            }
        }
        lifecycleScope.launch {
            settingsViewModel.loadFont(applicationContext)
            workManagementViewModel.initialize { defaultWorks(assets) }
            sessionReady = true
        }
    }

    private fun installUpdate() {
        if (openingInstaller) return
        fun editorCanRestart(): Boolean = sessionReady && !workManagementViewModel.workSaving &&
            !sessionViewModel.assetBusy && sessionViewModel.snapshotOperationWorkId == null &&
            !workManagementViewModel.hasEditsToSave() && !recordingViewModel.isPreviewRecording &&
            !recordingViewModel.isRecordingSaving && recordingViewModel.pendingRecordingFormat == null
        fun reportPendingEdits() {
            updateViewModel.report(if (recordingViewModel.isPreviewRecording || recordingViewModel.isRecordingSaving ||
                recordingViewModel.pendingRecordingFormat != null) "録画や保存が終わってから、更新をインストールしてください"
                else "編集中の作品を保存してから、更新をインストールしてください")
        }
        if (!editorCanRestart()) {
            reportPendingEdits()
            return
        }
        openingInstaller = true
        lifecycleScope.launch {
            try {
                val file = updateViewModel.validateReady()
                if (!editorCanRestart()) {
                    reportPendingEdits()
                    return@launch
                }
                if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                    updateViewModel.report("このアプリからのインストールを許可してください")
                    installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")))
                } else {
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateViewModel.discardReady()
                updateViewModel.report("インストールを開始できませんでした。APKを再ダウンロードするか、配布ページを確認してください")
            } finally {
                openingInstaller = false
            }
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
