package com.hikariatelier.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private fun Color.toHexCss(): String =
    String.format(Locale.US, "#%06x", toArgb() and 0xFFFFFF)

internal const val BLOCK_EDITOR_ORIGIN = "https://blocks.edit-rin.invalid"
private const val BLOCK_EDITOR_URL = "$BLOCK_EDITOR_ORIGIN/blocks/index.html"
private const val MAX_BLOCK_MESSAGE = 12 * 1024 * 1024

/** This client serves application assets only. It never receives project files or preview APIs. */
internal class BlockEditorWebClient(
    private val assets: AssetManager,
    private val ready: (WebView) -> Unit,
    private val failed: () -> Unit
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

    override fun onPageFinished(view: WebView?, url: String?) {
        if (view != null && url == BLOCK_EDITOR_URL) ready(view)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
        val uri = request?.url
        val path = uri?.path.orEmpty()
        if (uri?.scheme != "https" || uri.host != "blocks.edit-rin.invalid" ||
            uri.port != -1 || !path.startsWith("/blocks/") ||
            path.split('/').any { it == ".." || it == "." } || path.contains('\\')) return denied()
        return runCatching {
            val mime = when (path.substringAfterLast('.').lowercase()) {
                "html" -> "text/html"
                "js", "mjs" -> "application/javascript"
                "css" -> "text/css"
                "json", "map" -> "application/json"
                "svg" -> "image/svg+xml"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "cur" -> "image/x-icon"
                "woff", "woff2" -> "font/${path.substringAfterLast('.')}"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
                else -> return denied()
            }
            WebResourceResponse(mime, "UTF-8", 200, "OK", mapOf(
                "Cache-Control" to "no-store",
                "X-Content-Type-Options" to "nosniff",
                "Content-Security-Policy" to "default-src 'none'; script-src 'self'; worker-src 'self'; " +
                    "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; " +
                    "media-src 'self'; connect-src 'self'; object-src 'none'; frame-src 'none'; base-uri 'none'"
            ), assets.open("public$path"))
        }.getOrElse { denied() }
    }

    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
        (view?.parent as? android.view.ViewGroup)?.removeView(view)
        runCatching { view?.destroy() }
        failed()
        return true
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
        if (request?.isForMainFrame == true) failed()
    }

    private fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden",
        emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

/** Kotlin owns acknowledged edits; the WebView can be discarded and reconstructed at any time. */
@SuppressLint("SetJavaScriptEnabled")
private class BlockEditorHost(
    context: Context,
    private val onMessage: (JSONObject) -> Unit,
    private val onReady: () -> Unit,
    private val onFailure: () -> Unit,
    private val onDraft: (JSONObject) -> Boolean
) {
    val sessionId = UUID.randomUUID().toString()
    private var port: WebMessagePort? = null
    private var disposed = false
    private var ready = false
    private val startupHandler = Handler(Looper.getMainLooper())
    private val startupTimeout = Runnable { if (!disposed && !ready) onFailure() }
    private var document: JSONObject? = null
    private var sentDocument: String? = null
    private var sentFullscreen: Boolean? = null
    private val flushRequests = mutableMapOf<String, CompletableDeferred<Boolean>>()
    val view = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.mediaPlaybackRequiresUserGesture = true
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webViewClient = BlockEditorWebClient(context.assets, { connected ->
            if (!disposed && port == null) {
                val channel = connected.createWebMessageChannel()
                port = channel[0]
                channel[0].setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
                    override fun onMessage(port: WebMessagePort?, message: WebMessage?) {
                        if (disposed) return
                        val data = message?.data ?: return
                        if (data.length > MAX_BLOCK_MESSAGE) return
                        val json = runCatching { JSONObject(data) }.getOrNull() ?: return
                        if (json.optString("sessionId") != sessionId) return
                        if (json.optString("type") == "ready") {
                            ready = true
                            startupHandler.removeCallbacks(startupTimeout)
                            onReady()
                            flush()
                        } else if (json.optString("type") == "flushed") {
                            flushRequests.remove(json.optString("requestId"))?.let { completion ->
                                completion.complete(json.optBoolean("ok", false) && onDraft(json))
                            }
                        } else onMessage(json)
                    }
                })
                connected.postWebMessage(WebMessage(JSONObject().put("type", "connect")
                    .put("sessionId", sessionId).toString(), arrayOf(channel[1])), Uri.parse(BLOCK_EDITOR_ORIGIN))
                sentDocument = null
                flush()
            }
        }, onFailure)
        startupHandler.postDelayed(startupTimeout, 15_000L)
        loadUrl(BLOCK_EDITOR_URL)
    }

    fun update(next: JSONObject) {
        document = next.put("sessionId", sessionId)
        flush()
    }

    private fun flush() {
        val serialized = document?.toString() ?: return
        if (serialized == sentDocument || port == null || !ready || disposed) return
        runCatching { port?.postMessage(WebMessage(serialized)) }.onSuccess { sentDocument = serialized }
    }

    fun updateErrors(errorsJson: JSONArray) {
        if (!ready || disposed) return
        val msg = JSONObject().put("type", "errors")
            .put("sessionId", sessionId)
            .put("errors", errorsJson)
        runCatching { port?.postMessage(WebMessage(msg.toString())) }
    }

    fun setFullscreen(fullscreen: Boolean) {
        if (!ready || disposed || sentFullscreen == fullscreen) return
        runCatching { port?.postMessage(WebMessage(JSONObject().put("type", "viewState")
            .put("sessionId", sessionId).put("fullscreen", fullscreen).toString())) }
            .onSuccess { sentFullscreen = fullscreen }
    }

    fun command(action: String, values: JSONObject = JSONObject()) {
        if (!ready || disposed) return
        val current = document ?: return
        val message = JSONObject(values.toString()).put("type", "uiCommand").put("action", action)
            .put("sessionId", sessionId).put("workId", current.optString("workId"))
            .put("expectedRevision", current.optString("revision"))
        runCatching { port?.postMessage(WebMessage(message.toString())) }
    }

    fun acknowledge(accepted: Boolean, workId: String, source: String, metadata: String?, revision: String,
                    requestId: String? = null, expectedRevision: String? = null) {
        val reply = blockEditAckMessage(sessionId, accepted, workId, source, metadata, revision, requestId, expectedRevision)
        runCatching { port?.postMessage(WebMessage(reply.toString())) }
        // An immediate Undo may return to the last sent load before Compose observes this ACK.
        sentDocument = null
    }

    suspend fun flushEdits(hideChaff: Boolean = true): Boolean {
        if (disposed) return false
        if (!ready) return true // No document can be edited before the page acknowledges the channel.
        val currentPort = port ?: return true
        val requestId = UUID.randomUUID().toString()
        val completion = CompletableDeferred<Boolean>()
        flushRequests[requestId] = completion
        return try {
            currentPort.postMessage(WebMessage(JSONObject().put("type", "flush")
                .put("sessionId", sessionId).put("requestId", requestId)
                .put("hideChaff", hideChaff).toString()))
            val result = withTimeoutOrNull(5_000L) { completion.await() }
            if (result == null) {
                // Keep an escape to acknowledged native source even when the renderer stops replying.
                // Only dispose the unresponsive webview on explicit dismiss/save flushes (hideChaff = true),
                // not on transient background auto-draft attempts.
                if (hideChaff) {
                    dispose()
                    onFailure()
                }
                false
            } else result
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { false }
        finally { flushRequests.remove(requestId) }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        startupHandler.removeCallbacks(startupTimeout)
        flushRequests.values.forEach { it.complete(false) }
        flushRequests.clear()
        runCatching { port?.close() }
        port = null
        runCatching {
            view.stopLoading()
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.destroy()
        }
    }
}

@Composable
internal fun BlockWorkspaceView(
    work: Work,
    source: String,
    session: EditorSessionViewModel,
    language: String,
    dark: Boolean,
    readOnly: Boolean,
    errors: List<ConsoleEntry>,
    onOpenCode: (Int, Int) -> Unit,
    onOpenRuntime: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    documentBar: @Composable (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presentationStore = remember(context) { BlockEditorPresentationStore(context) }
    var displayPreferences by remember(presentationStore) { mutableStateOf(presentationStore.displayPreferences()) }
    val restoredViewport = remember(work.id) { session.blockEditorViewport(work.id) ?: presentationStore.viewport(work.id) }
    var generation by remember(work.id) { mutableStateOf(0) }
    var fullscreen by remember(work.id) { mutableStateOf(false) }
    var hostFailure by remember(work.id) { mutableStateOf(false) }
    var hostReady by remember(work.id, generation) { mutableStateOf(false) }
    val chrome = remember(work.id, generation) { BlockEditorChromeModel() }
    val currentOpenCode by rememberUpdatedState(onOpenCode)
    val currentOpenRuntime by rememberUpdatedState(onOpenRuntime)
    val currentUndo by rememberUpdatedState(onUndo)
    val currentRedo by rememberUpdatedState(onRedo)
    val currentReadOnly by rememberUpdatedState(readOnly)
    val metadata = session.currentBlockEditorState(work.id)
    val revision = session.currentBlockDocumentRevision(work.id)
    val effectiveFiles = effectiveProjectFiles(work.id, work.files.toMap(), session.fileDrafts)
    val sourceType = remember(work.id, source, effectiveFiles) {
        blockEditorSourceType(work.id, source, effectiveFiles, emptyMap())
    }
    val colors = MaterialTheme.colorScheme
    val surfaceArgb = colors.surface.toArgb()
    val themeJson = remember(
        colors.background, colors.surface, colors.surfaceContainerLow, colors.surfaceContainer,
        colors.surfaceContainerHigh, colors.surfaceContainerHighest, colors.surfaceContainerLowest,
        colors.onSurface, colors.onSurfaceVariant, colors.outline, colors.outlineVariant,
        colors.primary, colors.onPrimary, colors.primaryContainer, colors.onPrimaryContainer,
        colors.secondaryContainer, colors.onSecondaryContainer,
        colors.error, colors.errorContainer, colors.onErrorContainer
    ) {
        JSONObject()
            .put("background", colors.background.toHexCss())
            .put("surface", colors.surface.toHexCss())
            .put("surfaceContainerLow", colors.surfaceContainerLow.toHexCss())
            .put("surfaceContainer", colors.surfaceContainer.toHexCss())
            .put("surfaceContainerHigh", colors.surfaceContainerHigh.toHexCss())
            .put("surfaceContainerHighest", colors.surfaceContainerHighest.toHexCss())
            .put("surfaceContainerLowest", colors.surfaceContainerLowest.toHexCss())
            .put("onSurface", colors.onSurface.toHexCss())
            .put("onSurfaceVariant", colors.onSurfaceVariant.toHexCss())
            .put("outline", colors.outline.toHexCss())
            .put("outlineVariant", colors.outlineVariant.toHexCss())
            .put("primary", colors.primary.toHexCss())
            .put("onPrimary", colors.onPrimary.toHexCss())
            .put("primaryContainer", colors.primaryContainer.toHexCss())
            .put("onPrimaryContainer", colors.onPrimaryContainer.toHexCss())
            .put("secondaryContainer", colors.secondaryContainer.toHexCss())
            .put("onSecondaryContainer", colors.onSecondaryContainer.toHexCss())
            .put("error", colors.error.toHexCss())
            .put("errorContainer", colors.errorContainer.toHexCss())
            .put("onErrorContainer", colors.onErrorContainer.toHexCss())
    }
    val payload = remember(work.id, source, metadata, revision, language, dark, themeJson, readOnly,
        work.p5Version, work.p5SoundEnabled, work.assets.toMap(), errors, sourceType, displayPreferences) {
        JSONObject().put("type", "load").put("workId", work.id).put("file", "sketch.js")
            .put("nativeChrome", true)
            .put("source", source).put("revision", revision)
            .put("metadata", metadata ?: JSONObject.NULL).put("language", language)
            .put("dark", dark).put("theme", themeJson).put("readOnly", readOnly).put("sourceType", sourceType)
            .put("viewport", restoredViewport?.toJson() ?: JSONObject.NULL)
            .put("preferences", displayPreferences.toJson())
            .put("p5Version", work.p5Version).put("soundEnabled", work.p5SoundEnabled)
            .put("assets", JSONArray().apply { work.assets.forEach { (name, asset) ->
                put(JSONObject().put("name", name).put("mime", asset.mime))
            } }).put("errors", JSONArray().apply { errors.forEach { error ->
                put(JSONObject().put("line", error.line).put("message", error.message))
            } })
    }
    val host = remember(work.id, generation) {
        lateinit var created: BlockEditorHost
        created = BlockEditorHost(context, messageHandler@{ message ->
            if (message.optString("workId", work.id) != work.id || session.activeWorkIdState.value != work.id) {
                return@messageHandler
            }
            val owner = work.id
            val activation = session.blockEditorActivationGeneration
            val requestedRevision = (message.opt("expectedRevision") as? String)?.takeIf { it.isNotBlank() }
            fun currentAction(requireEditable: Boolean): Boolean = requestedRevision != null &&
                session.canApplyBlockAction(owner, activation, requestedRevision, requireEditable) &&
                (!requireEditable || !currentReadOnly)
            when (message.optString("type")) {
                "draftChanged" -> if (currentAction(requireEditable = true)) session.notifyBlockInputChanged()
                "chrome" -> if (message.optString("expectedRevision") == session.currentBlockDocumentRevision(owner)) {
                    decodeBlockChromeState(message)?.let { chrome.state = it }
                }
                "searchResults" -> chrome.acceptResults(message, session.currentBlockDocumentRevision(owner))
                "openSearch" -> chrome.openSearch("search")
                "view" -> decodeBlockEditorViewport(message.optJSONObject("viewport"))?.let { viewport ->
                    if (session.rememberBlockEditorViewport(owner, viewport)) presentationStore.saveViewport(owner, viewport)
                }
                "preferences" -> decodeBlockEditorDisplayPreferences(message.optJSONObject("preferences"))?.let { preferences ->
                    presentationStore.saveDisplayPreferences(preferences)
                    displayPreferences = preferences
                }
                "edit" -> {
                    val transaction = decodeBlockEditTransaction(owner, message)
                    val accepted = !currentReadOnly && transaction != null && session.applyBlockEdit(transaction)
                    created.acknowledge(accepted, owner, session.editorValueState.value.text,
                        session.currentBlockEditorState(owner), session.currentBlockDocumentRevision(owner),
                        (message.opt("requestId") as? String)?.takeIf { it.isNotBlank() }, requestedRevision)
                }
                "toggleFullscreen" -> scope.launch {
                    if (session.flushBlockEditor() && currentAction(requireEditable = false)) {
                        if (fullscreen) fullscreen = false
                        session.blockSplitView = !session.blockSplitView
                    }
                }
                "openCode" -> scope.launch {
                    if (session.flushBlockEditor() && currentAction(requireEditable = false)) {
                        fullscreen = false
                        currentOpenCode(message.optInt("start", 0), message.optInt("end", 0))
                    }
                }
                "openRuntime" -> if (!currentReadOnly) scope.launch {
                    if (session.flushBlockEditor() && currentAction(requireEditable = true)) { fullscreen = false; currentOpenRuntime() }
                }
                "undo" -> if (!currentReadOnly) scope.launch {
                    if (session.flushBlockEditor() && currentAction(requireEditable = true)) currentUndo()
                }
                "redo" -> if (!currentReadOnly) scope.launch {
                    if (session.flushBlockEditor() && currentAction(requireEditable = true)) currentRedo()
                }
                "retry" -> { hostFailure = false; generation++ }
            }
        }, { hostReady = true }, { hostFailure = true }, draftHandler@{ message ->
            val expected = message.opt("expectedRevision") as? String ?: return@draftHandler false
            if (message.optString("workId") != work.id ||
                !session.canApplyBlockAction(work.id, session.blockEditorActivationGeneration, expected,
                    requireEditable = false)) return@draftHandler false
            val draft = message.optJSONObject("draft")
            if (draft == null) {
                session.clearBlockInputDraft()
                true
            } else {
                if (draft.optString("requestId") != message.optString("requestId") ||
                    draft.optString("expectedRevision") != expected) return@draftHandler false
                val transaction = decodeBlockEditTransaction(work.id, draft) ?: return@draftHandler false
                !currentReadOnly && session.stageBlockInputDraft(transaction)
            }
        })
        created
    }
    SideEffect {
        if (!hostFailure) {
            host.view.setBackgroundColor(surfaceArgb)
        }
    }
    LaunchedEffect(payload, hostFailure) {
        if (!hostFailure) {
            host.update(payload)
        }
    }
    val isFullscreenWorkspace = !session.blockSplitView || fullscreen
    LaunchedEffect(isFullscreenWorkspace, hostFailure, hostReady) {
        if (!hostFailure) {
            host.setFullscreen(isFullscreenWorkspace)
        }
    }
    LaunchedEffect(errors, hostReady) {
        if (!hostFailure && hostReady) {
            val jsonErrors = JSONArray().apply {
                errors.forEach { error ->
                    put(JSONObject().put("line", error.line).put("message", error.message))
                }
            }
            host.updateErrors(jsonErrors)
        }
    }
    val flusher = remember(host) { suspend { hideChaff: Boolean -> host.flushEdits(hideChaff) } }
    DisposableEffect(host) {
        session.blockEditorFlusher = flusher
        onDispose {
            if (session.blockEditorFlusher === flusher) session.blockEditorFlusher = null
            host.dispose()
        }
    }
    fun openSearch(mode: String) {
        host.command("dismiss")
        chrome.openSearch(mode)
    }
    val searchAction = remember(host) { {
        if (session.activeWorkIdState.value == work.id && session.blockModeState(work.id).value) openSearch("search")
    } }
    DisposableEffect(searchAction) {
        session.blockEditorSearch = searchAction
        onDispose { if (session.blockEditorSearch === searchAction) session.blockEditorSearch = null }
    }
    LaunchedEffect(host, chrome.searchMode, chrome.query, revision, chrome.state.busy, hostReady) {
        if (chrome.searchMode == null) return@LaunchedEffect
        chrome.invalidateSearch()
        if (!hostReady || chrome.state.busy) return@LaunchedEffect
        delay(120)
        val requestId = chrome.beginSearch()
        host.command("search", JSONObject().put("requestId", requestId)
            .put("mode", chrome.searchMode).put("query", chrome.query))
    }
    val uiText = { value: String -> textTranslator(value, emptyArray()) }
    fun toggleFullscreen() {
        val activation = session.blockEditorActivationGeneration
        val nextSplitView = !session.blockSplitView || fullscreen
        scope.launch {
            if (session.flushBlockEditor() && session.activeWorkIdState.value == work.id &&
                session.canApplyBlockAction(work.id, activation, requireEditable = false)) {
                fullscreen = false
                session.blockSplitView = nextSplitView
            }
        }
    }
    // Keep one host and message channel when moving between the pane and dialog.
    // Changing presentation must not reload the document or discard pending fields.
    @Composable
    fun WorkspaceContent() {
        BackHandler(enabled = chrome.state.overlay && !hostFailure) { host.command("dismiss") }
        Column(Modifier.fillMaxSize()) {
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
                    documentBar(!chrome.state.overlay || hostFailure)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (hostFailure) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            textTranslator("ブロック画面を再読み込みしてください。確定済みの編集内容は保持されています。", emptyArray()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.FilledTonalButton(onClick = { hostFailure = false; generation++ }) {
                                Text(textTranslator("再読み込み", emptyArray()))
                            }
                            androidx.compose.material3.TextButton(onClick = {
                                // Explicit recovery uses the native acknowledged document. Never ask
                                // the failed renderer to flush again or accept its late messages.
                                if (session.blockEditorFlusher === flusher &&
                                    session.canApplyBlockAction(work.id, session.blockEditorActivationGeneration,
                                        requireEditable = false)) {
                                    host.dispose()
                                    session.blockEditorFlusher = null
                                    fullscreen = false
                                    currentOpenCode(0, 0)
                                }
                            }) {
                                Text(textTranslator("コードで開く", emptyArray()))
                            }
                        }
                    }
                } else {
                    // AndroidView factories run once per composition identity. A replacement host must
                    // replace its view too; the previous host is destroyed by DisposableEffect.
                    key(host) { AndroidView(factory = {
                        (host.view.parent as? android.view.ViewGroup)?.removeView(host.view)
                        host.view
                    }, modifier = Modifier.fillMaxSize()) }
                    if (!hostReady) androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                    BlockEditorFloatingStatusPill(
                        model = chrome,
                        ready = hostReady && !hostFailure,
                        text = uiText,
                        onCommand = { host.command(it) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 12.dp, top = 10.dp, end = 148.dp)
                    )
                    BlockEditorFloatingDock(
                        model = chrome,
                        ready = hostReady && !hostFailure,
                        fullscreen = isFullscreenWorkspace,
                        canUndo = !readOnly && session.undoStack.isNotEmpty(),
                        canRedo = !readOnly && session.redoStack.isNotEmpty(),
                        text = uiText,
                        onCommand = { host.command(it) },
                        onSearch = ::openSearch,
                        onDisplay = { chrome.showDisplay = true },
                        onFullscreen = ::toggleFullscreen,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
            BlockEditorSearchDialog(chrome, uiText, onQuery = { chrome.query = it.take(256); chrome.invalidateSearch() }) { entry ->
                if (chrome.resultRevision == session.currentBlockDocumentRevision(work.id)) {
                    chrome.closeSearch()
                    host.command("focus", JSONObject().put("id", entry.id))
                }
            }
            if (chrome.showDisplay) BlockEditorDisplayDialog(displayPreferences, uiText,
                onDismiss = { chrome.showDisplay = false }, onApply = { preferences ->
                    chrome.showDisplay = false
                    host.command("displayPreferences", JSONObject().put("preferences", preferences.toJson()))
                })
        }
    }
    Box(modifier) {
        if (!fullscreen) WorkspaceContent()
    }
    if (fullscreen) Dialog(
        onDismissRequest = { if (chrome.state.overlay && !hostFailure) host.command("dismiss") else fullscreen = false },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Use the same edge-to-edge window policy as the expanded preview. The
        // dialog default reserves the entire cutout strip even around the canvas.
        KeepLandscapeDialogImmersive(forceFullscreen = true)
        androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().imePadding()) {
                Box(Modifier.weight(1f)) { WorkspaceContent() }
            }
        }
    }
}
