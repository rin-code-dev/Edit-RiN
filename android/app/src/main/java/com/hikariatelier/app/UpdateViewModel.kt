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

    fun discardReady() { readyApk = null }

    fun report(message: String) { this.message = message }

    fun cancelDownload() { downloadJob?.cancel() }

    fun download() {
        val release = availableRelease ?: return
        if (checking || downloading || release.apk == null) return
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

    fun dismiss() { if (!downloading) message = null }

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
}
