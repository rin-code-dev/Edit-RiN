package com.hikariatelier.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One in-flight request per activity session; retained across device rotation. */
internal class UpdateViewModel : ViewModel() {
    var checking by mutableStateOf(false)
        private set
    var manualChecking by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var availableRelease by mutableStateOf<AppRelease?>(null)
        private set
    private var startupChecked = false

    fun dismiss() { message = null }

    fun checkAtStartup() {
        if (startupChecked) return
        startupChecked = true
        check(silent = true)
    }

    fun checkManually() = check(silent = false)

    private fun check(silent: Boolean) {
        if (checking) return
        checking = true
        manualChecking = !silent
        viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) { fetchNewestRelease() }
                val result = evaluateUpdate(release, BuildConfig.VERSION_NAME, silent)
                availableRelease = result.release
                message = result.message
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                availableRelease = null
                message = updateFailureMessage(silent)
            } finally {
                checking = false
                manualChecking = false
            }
        }
    }
}
