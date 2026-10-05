package com.hikariatelier.app

import android.content.res.AssetManager
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

internal const val PREVIEW_ORIGIN = "https://appassets.androidplatform.net"
internal fun previewUrl(token: String) = "$PREVIEW_ORIGIN/project/$token/p5_runner.html"
internal fun previewUrl(assets: PreviewAssets) = assets.documentPath?.let { projectFileUrl(assets.token, it) } ?: previewUrl(assets.token)

internal data class PreviewAssets(
    val token: String,
    val assets: Map<String, ProjectAsset>,
    val virtualFiles: Map<String, String> = emptyMap(),
    val documentPath: String? = null
)

/** Serves only the active preview's files under a local HTTPS origin. */
internal class AssetWebClient(
    private val bundled: AssetManager,
    private val storage: AssetStorage,
    private val onRendererCrash: ((Boolean) -> Unit)? = null,
    private val onExternalNavigation: ((Uri) -> Unit)? = null,
    private val snapshot: () -> PreviewAssets
) : WebViewClient() {
    @androidx.annotation.RequiresApi(26)
    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
        try {
            (view?.parent as? android.view.ViewGroup)?.removeView(view)
            view?.destroy()
        } catch (_: Exception) {}
        onRendererCrash?.invoke(detail?.didCrash() == true)
        return true
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request?.isForMainFrame != true) return false
        val state = snapshot()
        val relative = projectPathFromUrl(request.url.toString(), state.token)
        if (relative == "p5_runner.html" || relative != null && isHtmlProjectFile(relative) && relative in state.virtualFiles) return false
        if (request.hasGesture() && request.url.scheme in setOf("https", "http") &&
            request.url.host != "appassets.androidplatform.net") onExternalNavigation?.invoke(request.url)
        return true
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val uri = request?.url ?: return null
        if (uri.scheme != "https" || uri.host != "appassets.androidplatform.net") return null
        val state = snapshot()
        val runtimePrefix = "$PREVIEW_ORIGIN/project/${state.token}/$PROJECT_RUNTIME_DIRECTORY/"
        if (uri.toString().startsWith(runtimePrefix)) {
            val name = uri.toString().removePrefix(runtimePrefix)
            if (name !in setOf("p5_host.js", "p5_bootstrap.js", "p5_sketch.js")) return missing()
            return try { WebResourceResponse("application/javascript", "UTF-8", bundled.open("public/$name")) }
                catch (_: Exception) { missing() }
        }
        val relative = projectPathFromUrl(uri.toString(), state.token) ?: return missing()
        return try {
            if (relative == "p5_runner.html" && state.documentPath == null)
                return WebResourceResponse("text/html", "UTF-8", bundled.open("public/p5_runner.html"))
            when (relative) {
                else -> if (state.virtualFiles.containsKey(relative)) {
                    val bytes = state.virtualFiles.getValue(relative).toByteArray(Charsets.UTF_8)
                    val headers = mutableMapOf("Content-Length" to bytes.size.toString(), "Cache-Control" to "no-store")
                    if ("$relative.map" in state.virtualFiles) headers["SourceMap"] = projectFileUrl(state.token, "$relative.map")
                    return WebResourceResponse(projectTextMimeType(relative), "UTF-8", 200, "OK", headers, ByteArrayInputStream(bytes))
                }
            }
            when (relative) {
                "p5_runner.html" -> WebResourceResponse("text/html", "UTF-8", bundled.open("public/p5_runner.html"))
                "p5.min.js", "p5-v1.min.js", "p5-v2.min.js", "p5.sound.min.js", "p5.sound-v1.min.js",
                "p5.brush-2.2.1.js", "matter-0.20.0.min.js", "p5.webgpu.js" ->
                    WebResourceResponse("application/javascript", "UTF-8", bundled.open("public/${if (relative == "p5.min.js") "p5-v1.min.js" else relative}"))
                else -> {
                    val virtualContent = if (relative.startsWith("assets/")) state.virtualFiles[relative.removePrefix("assets/")] else null
                    if (virtualContent != null) {
                        val bytes = virtualContent.toByteArray(Charsets.UTF_8)
                        val mime = projectTextMimeType(relative)
                        return WebResourceResponse(mime, "UTF-8", 200, "OK",
                            mapOf("Content-Length" to bytes.size.toString(), "Cache-Control" to "no-store"),
                            ByteArrayInputStream(bytes))
                    }
                    val name = if (relative in state.assets) relative else relative.removePrefix("assets/")
                    if (!validAssetName(name)) return missing()
                    val asset = state.assets[name] ?: return missing()
                    if (!storage.contains(asset)) return missing()
                    val rangeHeader = request.requestHeaders.entries.firstOrNull { it.key.equals("Range", true) }?.value
                    val range = assetByteRange(rangeHeader, asset.size)
                    if (rangeHeader != null && range == null) {
                        return WebResourceResponse(asset.mime, null, 416, "Range Not Satisfiable",
                            mapOf("Content-Range" to "bytes */${asset.size}"), ByteArrayInputStream(byteArrayOf()))
                    }
                    val start = range?.first ?: 0L
                    val length = range?.let { it.last - it.first + 1 } ?: asset.size
                    val stream = storage.file(asset).inputStream()
                    stream.channel.position(start)
                    val headers = mutableMapOf("Content-Length" to length.toString(), "Accept-Ranges" to "bytes", "Cache-Control" to "no-store")
                    if (range != null) headers["Content-Range"] = "bytes $start-${range.last}/${asset.size}"
                    WebResourceResponse(asset.mime, null, if (range == null) 200 else 206,
                        if (range == null) "OK" else "Partial Content", headers, LimitedAssetStream(stream, length))
                }
            }
        } catch (_: Exception) { missing() }
    }
    private fun missing() = WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
        mapOf("Cache-Control" to "no-store"), ByteArrayInputStream("Asset not found".toByteArray()))
}
