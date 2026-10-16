package com.hikariatelier.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import java.io.File
import kotlinx.coroutines.Job
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One in-flight request per activity session; retained across device rotation. */
internal class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val apkRepository = UpdateApkRepository(application)
    private var downloadJob: Job? = null
    var downloading by mutableStateOf(false)
        private set
    var downloadProgress by mutableFloatStateOf(0f)
        private set
    var readyApk by mutableStateOf<File?>(null)
        private set
    var isTestPreview by mutableStateOf(false)
        private set

    fun discardReady() { readyApk = null }

    fun report(message: String) { this.message = message }

    fun cancelDownload() { downloadJob?.cancel() }

    fun download() {
        val release = availableRelease ?: return
        if (checking || downloading || release.apk == null) return
        if (isTestPreview) {
            startTestDownloadSimulation()
            return
        }
        if (BuildConfig.DEBUG) {
            report("デバッグ版はアプリ内更新に対応していません。配布ページから正式版を入手してください")
            return
        }
        downloading = true
        downloadProgress = 0f
        readyApk = null
        report("更新をダウンロード中…")
        downloadJob = viewModelScope.launch {
            try {
                readyApk = apkRepository.download(release) { value ->
                    withContext(Dispatchers.Main) { downloadProgress = value }
                }
                report("ダウンロードが完了しました。インストールして更新できます")
            } catch (cancelled: CancellationException) {
                report("ダウンロードを中止しました")
                throw cancelled
            } catch (_: Exception) {
                report("更新をダウンロードできませんでした。通信環境や空き容量を確認して、もう一度お試しください")
            } finally {
                downloading = false
            }
        }
    }

    suspend fun validateReady(): File = withContext(Dispatchers.IO) {
        val file = requireNotNull(readyApk)
        apkRepository.validate(file, requireNotNull(availableRelease))
        file
    }
    var checking by mutableStateOf(false)
        private set
    var manualChecking by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var availableRelease by mutableStateOf<AppRelease?>(null)
        private set
    private var startupChecked = false

    fun dismiss() {
        if (!downloading) {
            message = null
            if (isTestPreview) {
                isTestPreview = false
                availableRelease = null
                readyApk = null
                downloadProgress = 0f
            }
        }
    }

    fun checkAtStartup() {
        if (startupChecked) return
        startupChecked = true
        check(silent = true)
    }

    fun checkManually() = check(silent = false)

    private fun check(silent: Boolean) {
        if (checking || downloading) return
        checking = true
        manualChecking = !silent
        viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) { fetchNewestRelease() }
                val result = evaluateUpdate(release, BuildConfig.VERSION_NAME, silent)
                if (result.release?.tag != availableRelease?.tag) readyApk = null
                availableRelease = result.release
                message = result.message
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                availableRelease = null
                readyApk = null
                message = updateFailureMessage(silent)
            } finally {
                checking = false
                manualChecking = false
            }
        }
    }

    fun showTestPreview() {
        val testVersion = ReleaseVersion.parse("v3.2.0") ?: return
        isTestPreview = true
        downloadProgress = 0f
        readyApk = null
        availableRelease = AppRelease(
            tag = "v3.2.0",
            version = testVersion,
            apk = ReleaseAsset(url = "https://github.com/rin-code-dev/Edit-RiN/releases/download/v3.2.0/Edit-RiN-v3.2.0.apk", size = 6_800_000L),
            highlights = listOf(
                UpdateHighlight(title = "コメント駆動パラメータ", description = "// @slider でUIスライダー出現＆双方向連動"),
                UpdateHighlight(title = "#つぶやきProcessing 支援", description = "140/280字HUDゲージと自動コード圧縮"),
                UpdateHighlight(title = "PiP小窓オートゴースト", description = "編集行との重なりで自動半透明化＆直接編集")
            )
        )
        message = "新しいバージョンがあります"
    }

    private fun startTestDownloadSimulation() {
        downloading = true
        downloadProgress = 0f
        readyApk = null
        report("更新をダウンロード中…")
        downloadJob = viewModelScope.launch {
            try {
                for (step in 1..100) {
                    kotlinx.coroutines.delay(25)
                    downloadProgress = step / 100f
                }
                val testFile = File(getApplication<Application>().cacheDir, "Edit-RiN-v3.2.0-test.apk")
                if (!testFile.exists()) testFile.writeText("Edit-RiN test apk payload")
                readyApk = testFile
                report("ダウンロードが完了しました。インストールして更新できます")
            } catch (cancelled: CancellationException) {
                report("ダウンロードを中止しました")
                throw cancelled
            } finally {
                downloading = false
            }
        }
    }
}
