package com.hikariatelier.app

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/** Event-driven queue, owned by the resumed Activity and destroyed on cancellation. */
internal class WorkPreviewWarmup(
    private val activity: ComponentActivity,
    private val storage: AssetStorage,
    private val works: WorkManagementViewModel,
    private val session: EditorSessionViewModel,
    private val images: PreviewImageRepository,
    private val metrics: PerformanceMeasurements,
    private val mediaBusy: () -> Boolean
) {
    private val failures = activity.getSharedPreferences("preview-warmup-failures", 0)
    private val bundled = BundledThumbnailCatalog(activity.assets)
    private val resources = ThumbnailResources(activity)
    private data class Candidate(val work: Work, val input: PreviewRunInput?, val updatedAt: Long)
    private val queue = ThumbnailQueue<Candidate>()
    private var queuedFolder: android.net.Uri? = null
    private var folderKnown = false
    private data class Collection(val folder: android.net.Uri?, val candidates: Map<String, Candidate>)
    private data class Interaction(val priority: List<String>, val active: String, val busy: Boolean, val unsaved: Boolean)

    suspend fun run() = coroutineScope {
        val wake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
        val budget = kotlinx.coroutines.flow.MutableStateFlow(0)
        val renderers = arrayOfNulls<ThumbnailRenderer>(2)
        val jobs = arrayOfNulls<Job>(2)
        val retryAfter = mutableMapOf<String, Long>()
        var collection: Collection? = null
        var interaction = Interaction(emptyList(), "", true, false)
        var memoryCheck: Job? = null
        var retryCheck: Job? = null
        fun signal() { wake.trySend(Unit) }
        fun busy() = mediaBusy() || works.workSaving || session.assetBusy ||
            session.snapshotOperationWorkId != null || works.thumbnailScrolling || works.loadFailure != null
        val subscriptions = listOf(
            launch {
                androidx.compose.runtime.snapshotFlow {
                    Collection(works.selectedFolderUri, session.worksState.value.associate { work ->
                        work.id to Candidate(work, if (work.bodyLoaded)
                            capturePreviewRun(work, work.code, work.files, work.assets, token = "") else null, work.updatedAt)
                    })
                }.collect { next ->
                    val folderChanged = folderKnown && queuedFolder != next.folder
                    folderKnown = true; queuedFolder = next.folder
                    if (folderChanged) {
                        jobs.forEach { it?.cancel(ThumbnailInterruptedException()) }
                        retryAfter.clear()
                    }
                    next.candidates.forEach { (id, candidate) -> if (!queue.matches(id, candidate)) retryAfter.remove(id) }
                    collection = next; queue.replace(next.candidates)
                    retryAfter.keys.retainAll(next.candidates.keys)
                    if (folderChanged) next.candidates.keys.forEach(queue::invalidate)
                    jobs.forEachIndexed { index, job ->
                        if (job != null && next.candidates.keys.none { it == runningIds[index] }) job.cancel(ThumbnailInterruptedException())
                    }
                    signal()
                }
            },
            launch {
                androidx.compose.runtime.snapshotFlow {
                    Interaction(works.thumbnailPriorityIds, session.activeWorkIdState.value, busy(), works.hasEditsToSave())
                }.collect { next ->
                    if (interaction.unsaved && !next.unsaved) queue.invalidate(next.active)
                    if (interaction.priority != next.priority || interaction.active != next.active)
                        (next.priority + next.active).distinct().forEach(queue::invalidate)
                    interaction = next; signal()
                }
            },
            launch { resources.changes.collect { signal() } },
            launch { metrics.loadChanges.collect { signal() } },
            launch {
                images.changes.collect { filename ->
                    if (filename == null) queue.ids().forEach(queue::invalidate)
                    else images.idForFile(filename)?.let(queue::invalidate)
                    signal()
                }
            }
        )
        try {
            for (ignored in wake) {
                val selected = collection ?: continue
                val deviceCount = if (interaction.busy) 0 else withContext(Dispatchers.IO) { resources.workerCount() }
                val load = metrics.loadState
                val count = when {
                    load.heavyLoad -> 0
                    load.jankActive -> (deviceCount - 1).coerceAtLeast(0)
                    else -> deviceCount
                }
                budget.value = if (busy()) 0 else count
                jobs.forEachIndexed { index, job -> if (index >= budget.value) job?.cancel(ThumbnailInterruptedException()) }
                val now = android.os.SystemClock.elapsedRealtime()
                val excluded = retryAfter.filterValues { it > now }.keys +
                    if (interaction.unsaved) setOf(interaction.active) else emptySet()
                for (index in 0..1) {
                    if (jobs[index] != null || index >= budget.value) continue
                    val (id, candidate) = queue.claim(interaction.priority, interaction.active, excluded) ?: continue
                    runningIds[index] = id
                    jobs[index] = launch {
                        var success = false
                        try {
                            val input = works.thumbnailInput(id, selected.folder) ?: error("Thumbnail input unavailable")
                            val fingerprint = withContext(Dispatchers.Default) { thumbnailFingerprint(input) }
                            val file = images.file(id)
                            val previousModified = withContext(Dispatchers.IO) { file.lastModified() }
                            val cached = withContext(Dispatchers.IO) { thumbnailCacheMatches(file, fingerprint) }
                            if (!cached && failures.getString(file.name, null) != fingerprint) {
                                val asset = withContext(Dispatchers.IO) { bundled.assetPath(candidate.work, input) }
                                val bytes = if (asset != null) withContext(Dispatchers.IO) {
                                    activity.assets.open(asset).use { it.readBytes() }
                                } else {
                                    val prepared = withContext(Dispatchers.Default) { preparePreviewRun(input) }
                                    val renderer = renderers[index]?.takeUnless { it.destroyed } ?: ThumbnailRenderer(activity, storage).also {
                                        renderers[index] = it
                                    }
                                    metrics.measureDifferentiated(
                                        PerformanceOperation.THUMBNAIL,
                                        PerformanceOperation.THUMBNAIL_INTERRUPTED,
                                        PerformanceOperation.THUMBNAIL_FAILED,
                                        isSuccess = { it != null }
                                    ) {
                                        renderer.render(prepared, budget.map { it <= index })
                                    }?.let { encoded -> withContext(Dispatchers.Default) {
                                        android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                                    } }
                                }
                                if (busy() || selected.folder != works.selectedFolderUri || !queue.matches(id, candidate) || index >= budget.value) return@launch
                                val current = works.thumbnailInput(id, selected.folder)
                                if (current == null || current.copy(token = input.token) != input) return@launch
                                if (bytes != null) {
                                    if (!images.store(id, bytes, previousModified, fingerprint)) return@launch
                                    works.notifyPreviewUpdated(id)
                                    withContext(Dispatchers.IO) { failures.edit().remove(file.name).apply() }
                                } else withContext(Dispatchers.IO) { failures.edit().putString(file.name, fingerprint).apply() }
                            }
                            success = true; retryAfter.remove(id)
                        } catch (_: ThumbnailInterruptedException) { /* Foreground work gets priority. */ }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { retryAfter[id] = android.os.SystemClock.elapsedRealtime() + 5000 }
                        finally {
                            queue.finish(id, candidate, success)
                            jobs[index] = null; runningIds[index] = null; signal()
                        }
                    }
                }
                // Memory has no reliable foreground pressure callback on newer Android releases.
                // Sample while work is active/pending under memory pressure, never when the queue is idle.
                if ((jobs.any { it != null } || queue.hasPending && !interaction.busy && deviceCount == 0) && memoryCheck?.isActive != true) {
                    memoryCheck = launch { delay(1500); memoryCheck = null; signal() }
                }
                val retry = retryAfter.values.filter { it > now }.minOrNull()
                if (retry != null && retryCheck?.isActive != true) retryCheck = launch { delay((retry - now).coerceAtLeast(1)); retryCheck = null; signal() }
                if (jobs.all { it == null }) {
                    renderers.forEachIndexed { index, renderer -> renderer?.close(); renderers[index] = null }
                    if (!queue.hasPending || interaction.busy) { memoryCheck?.cancel(); memoryCheck = null }
                }
            }
        } finally {
            subscriptions.forEach { it.cancel() }; memoryCheck?.cancel(); retryCheck?.cancel()
            jobs.filterNotNull().forEach { it.cancel() }
            renderers.forEach { it?.close() }; wake.close()
        }
    }
    private val runningIds = arrayOfNulls<String>(2)
}

@SuppressLint("SetJavaScriptEnabled")
private class ThumbnailRenderer(private val activity: ComponentActivity, storage: AssetStorage) {
    private val view = WebView(activity)
    var destroyed = false
        private set
    private var current: PreparedPreviewRun? = null
    private var ready: CompletableDeferred<Unit>? = null
    private val host = PreviewWebViewHost(activity, view).apply {
        setLogicalSize(480, 480)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isFocusable = false
    }
    private val parent = activity.findViewById<ViewGroup>(android.R.id.content)
    init {
        // Attached behind the editor so WebView can draw, without taking input or changing selection.
        parent.addView(host, 0, ViewGroup.LayoutParams(1, 1))
        view.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = false; allowFileAccess = false
            allowContentAccess = false; mediaPlaybackRequiresUserGesture = true; blockNetworkLoads = true
        }
        view.isFocusable = false
        view.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun getDefaultVideoPoster() = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        }
        view.webViewClient = AssetWebClient(activity.assets, storage, onRendererCrash = {
            destroyed = true
            parent.removeView(host)
            ready?.completeExceptionally(IllegalStateException("Thumbnail renderer stopped"))
        }) { current?.assets ?: PreviewAssets("", emptyMap()) }
    }
    suspend fun render(run: PreparedPreviewRun, shouldStop: Flow<Boolean>): String? {
        current = run
        val signal = CompletableDeferred<Unit>().also { ready = it }
        val captured = CompletableDeferred<String?>()
        view.removeJavascriptInterface("Android")
        view.addJavascriptInterface(ThumbnailBridge(run, signal, captured), "Android")
        view.loadUrl(previewUrl(run.assets))
        try {
            return captureThumbnailWhileAllowed(shouldStop) {
                withTimeoutOrNull(8000) {
                    // Setup readiness does not guarantee a first draw. Capture in
                    // the page after the actual draw lifecycle, then await its result.
                    signal.await()
                    view.evaluateJavascript("window.__editRinCaptureThumbnail?.()", null)
                    captured.await()
                }
            }
        } finally {
            ready = null
            if (!destroyed) { view.stopLoading(); view.loadUrl("about:blank") }
        }
    }
    fun close() {
        parent.removeView(host)
        if (!destroyed) {
            host.removeView(view); view.stopLoading(); view.removeJavascriptInterface("Android"); view.destroy()
            destroyed = true
        }
    }
}

internal class ThumbnailBridge(
    private val run: PreparedPreviewRun,
    private val ready: CompletableDeferred<Unit>,
    private val captured: CompletableDeferred<String?> = CompletableDeferred()
) {
    private fun current(token: String) = token == run.assets.token
    @JavascriptInterface fun onPreviewReady(token: String) { if (current(token)) ready.complete(Unit) }
    @JavascriptInterface fun onThumbnailReady(token: String, encoded: String) {
        if (current(token)) captured.complete(encoded.takeIf { it.isNotBlank() })
    }
    private fun failed(token: String) {
        if (current(token)) { ready.complete(Unit); captured.complete(null) }
    }
    @JavascriptInterface fun onError(token: String, message: String) = failed(token)
    @JavascriptInterface fun onRuntimeError(token: String, message: String, line: Int) = failed(token)
    @JavascriptInterface fun onRuntimeErrorFile(token: String, message: String, file: String, line: Int) = failed(token)
    @JavascriptInterface fun getSketchCode(token: String) = if (current(token)) run.sketchCode else ""
    @JavascriptInterface fun getP5Version(token: String) = if (current(token)) run.p5Version else ""
    @JavascriptInterface fun isP5SoundEnabled(token: String) = current(token) && run.soundEnabled
    @JavascriptInterface fun getWorkLibraries(token: String) = if (current(token)) run.libraries else "{}"
    @JavascriptInterface fun getWorkParameters(token: String) = if (current(token)) run.parameters else "{}"
    @JavascriptInterface fun getWorkShaders(token: String) = if (current(token)) run.shaders else "{}"
    @JavascriptInterface fun getProjectConfig(token: String) = if (current(token))
        JSONObject(run.projectConfig).put("thumbnailOnly", true).toString() else "{}"
}
