package com.hikariatelier.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File

/** A separate, local-only WebView that never receives user work or its assets. */
@SuppressLint("SetJavaScriptEnabled")
private class TemplatePreviewHost(context: Context, private val report: (String, Boolean) -> Unit) {
    val view = WebView(context)
    @Volatile private var current: PreparedPreviewRun? = null
    @Volatile private var terminalToken: String? = null
    private var destroyed = false
    private val main = Handler(Looper.getMainLooper())

    init {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            blockNetworkLoads = true
        }
        view.setBackgroundColor(android.graphics.Color.rgb(9, 9, 11))
        view.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        }
        view.setDownloadListener { _, _, _, _, _ -> }
        view.webViewClient = AssetWebClient(context.assets, AssetStorage(File(context.filesDir, "project-assets")),
            onRendererCrash = {
                destroyed = true
                current?.assets?.token?.let { token -> finish(token, false) }
            }) { current?.assets ?: PreviewAssets("", emptyMap()) }
        view.addJavascriptInterface(Bridge(), "Android")
    }

    fun start(kind: WorkTemplateKind): String {
        check(!destroyed)
        stop()
        val template = WorkTemplate("1:1", 400, 400, kind)
        val work = Work(id = "template-preview", title = "", code = template.code(CanvasSizingMode.FIXED),
            p5SoundEnabled = template.requiresSound, libraries = template.libraries)
        val run = preparePreviewRun(capturePreviewRun(work, work.code, emptyMap(), emptyMap()))
        current = run
        terminalToken = null
        view.loadUrl(previewUrl(run.assets))
        return run.assets.token
    }

    private fun finish(token: String, success: Boolean) {
        main.post {
            if (current?.assets?.token == token && (!success || terminalToken != token)) {
                if (!success) terminalToken = token
                report(token, success)
                if (!success) stop()
            }
        }
    }

    fun stop() {
        current = null
        terminalToken = null
        if (!destroyed) {
            runCatching { view.evaluateJavascript("window.rinDispose?.()", null) }
            view.stopLoading()
            view.loadUrl("about:blank")
        }
    }

    fun close() {
        stop()
        if (!destroyed) {
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.removeJavascriptInterface("Android")
            view.destroy()
            destroyed = true
        }
    }

    private inner class Bridge {
        private fun run(token: String) = current?.takeIf { it.assets.token == token }
        @JavascriptInterface fun onPreviewVisualReady(token: String) = finish(token, true)
        @JavascriptInterface fun onError(token: String, message: String) = finish(token, false)
        @JavascriptInterface fun onRuntimeError(token: String, message: String, line: Int) = finish(token, false)
        @JavascriptInterface fun onRuntimeErrorFile(token: String, message: String, file: String, line: Int) = finish(token, false)
        @JavascriptInterface fun getSketchCode(token: String) = run(token)?.sketchCode ?: ""
        @JavascriptInterface fun getP5Version(token: String) = run(token)?.p5Version ?: ""
        @JavascriptInterface fun isP5SoundEnabled(token: String) = run(token)?.soundEnabled == true
        @JavascriptInterface fun getWorkLibraries(token: String) = run(token)?.libraries ?: "{}"
        @JavascriptInterface fun getWorkParameters(token: String) = run(token)?.parameters ?: "{}"
        @JavascriptInterface fun getWorkShaders(token: String) = run(token)?.shaders ?: "{}"
        @JavascriptInterface fun getProjectConfig(token: String) = run(token)?.let {
            JSONObject(it.projectConfig).put("thumbnailOnly", true).toString()
        } ?: "{}"
    }
}

@Composable
internal fun TemplateLivePreview(
    kind: WorkTemplateKind,
    colors: ColorScheme,
    text: (String) -> String,
    modifier: Modifier = Modifier.fillMaxWidth().height(180.dp)
) {
    val context = LocalContext.current
    val lifecycle = LocalView.current.findViewTreeLifecycleOwner()?.lifecycle
    var started by remember(lifecycle) { mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) }
    var resumeGeneration by remember(lifecycle) { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf("loading") }
    var activeToken by remember { mutableStateOf("") }
    val host = remember(context, lifecycle, retry) {
        TemplatePreviewHost(context) { token, success ->
            if (token == activeToken) state = if (success) "ready" else "failed"
        }
    }
    DisposableEffect(lifecycle, host) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                started = true
                // ON_STOP and ON_START may be delivered before a recomposition.
                // Always restart the page that ON_STOP synchronously cleared.
                resumeGeneration++
            }
            if (event == Lifecycle.Event.ON_STOP) { started = false; host.stop() }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer); host.close() }
    }
    LaunchedEffect(host, kind, started, resumeGeneration) {
        if (!started) { host.stop(); activeToken = ""; return@LaunchedEffect }
        state = "loading"
        activeToken = runCatching { host.start(kind) }.getOrElse { state = "failed"; "" }
        val token = activeToken
        if (token.isNotEmpty()) {
            delay(8000)
            if (activeToken == token && state == "loading") { host.stop(); state = "failed" }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        key(host) { AndroidView(factory = { host.view }, modifier = Modifier.fillMaxSize()) }
        if (state == "loading") CircularProgressIndicator(Modifier.size(28.dp), color = colors.primary)
        if (state == "failed") Surface(color = colors.surfaceContainer, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(text("見本を表示できませんでした"), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { retry++ }) { Text(text("再試行")) }
            }
        }
    }
}
