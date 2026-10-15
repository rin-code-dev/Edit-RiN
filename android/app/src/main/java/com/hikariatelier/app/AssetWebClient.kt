package com.hikariatelier.app

import android.content.res.AssetManager
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

internal const val PREVIEW_ORIGIN = "https://appassets.androidplatform.net"
internal fun previewUrl(token: String) = "$PREVIEW_ORIGIN/project/$token/p5_runner.html"
internal fun previewUrl(assets: PreviewAssets) = assets.documentPath?.let { projectFileUrl(assets.token, it) } ?: previewUrl(assets.token)

internal data class PreviewAssets(
    val token: String,
    val assets: Map<String, ProjectAsset>,
    val virtualFiles: Map<String, String> = emptyMap(),
    val documentPath: String? = null
) {
    private val encodedVirtualFiles = ConcurrentHashMap<String, ByteArray>()

    internal fun virtualFileBytes(path: String): ByteArray? {
        val content = virtualFiles[path] ?: return null
        return encodedVirtualFiles.computeIfAbsent(path) { content.toByteArray(Charsets.UTF_8) }
    }
}

/**
 * AssetWebClient (v3.1):
 * - Caches decompressed APK bundled runtime/library scripts in memory on first access so subsequent
 *   preview runs serve 1MB+ p5.js/WebGPU/sound bundles in <1ms without APK zip decompression overhead.
 * - Preserves 100% per-run token isolation so p5.js 1.x/2.x global mode and relative assets never collide.
 */
internal class AssetWebClient(
    private val bundled: AssetManager,
    private val storage: AssetStorage,
    private val onRendererCrash: ((Boolean) -> Unit)? = null,
    private val onExternalNavigation: ((Uri) -> Unit)? = null,
    private val snapshot: () -> PreviewAssets
) : WebViewClient() {

    private fun bundledBytes(assetPath: String): ByteArray =
        BUNDLED_ASSET_CACHE.computeIfAbsent(assetPath) { path ->
            bundled.open(path).use { it.readBytes() }
        }

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
            return try {
                val bytes = bundledBytes("public/$name")
                WebResourceResponse(
                    "application/javascript",
                    "UTF-8",
                    200,
                    "OK",
                    mapOf("Content-Length" to bytes.size.toString(), "Cache-Control" to "no-store"),
                    ByteArrayInputStream(bytes)
                )
            } catch (_: Exception) { missing() }
        }
        val relative = projectPathFromUrl(uri.toString(), state.token) ?: return missing()
        return try {
            if (relative == "p5_runner.html" && state.documentPath == null) {
                val bytes = bundledBytes("public/p5_runner.html")
                return WebResourceResponse(
                    "text/html",
                    "UTF-8",
                    200,
                    "OK",
                    mapOf("Content-Length" to bytes.size.toString(), "Cache-Control" to "no-store"),
                    ByteArrayInputStream(bytes)
                )
            }
            state.virtualFileBytes(relative)?.let { bytes ->
                val headers = mutableMapOf("Content-Length" to bytes.size.toString(), "Cache-Control" to "no-store")
                if ("$relative.map" in state.virtualFiles) headers["SourceMap"] = projectFileUrl(state.token, "$relative.map")
                return WebResourceResponse(projectTextMimeType(relative), "UTF-8", 200, "OK", headers, ByteArrayInputStream(bytes))
            }
            when (relative) {
                "p5_runner.html" -> {
                    val bytes = bundledBytes("public/p5_runner.html")
                    WebResourceResponse(
                        "text/html",
                        "UTF-8",
                        200,
                        "OK",
                        mapOf("Content-Length" to bytes.size.toString(), "Cache-Control" to "no-store"),
                        ByteArrayInputStream(bytes)
                    )
                }
                "p5.min.js", "p5-v1.min.js", "p5-v2.min.js", "p5.sound.min.js", "p5.sound-v1.min.js",
                "p5.brush-2.2.1.js", "matter-0.20.0.min.js", "p5.webgpu.js" -> {
                    val actualName = if (relative == "p5.min.js") "p5-v1.min.js" else relative
                    val bytes = bundledBytes("public/$actualName")
                    WebResourceResponse(
                        "application/javascript",
                        "UTF-8",
                        200,
                        "OK",
                        mapOf(
                            "Content-Length" to bytes.size.toString(),
                            "Cache-Control" to "public, max-age=31536000, immutable"
                        ),
                        ByteArrayInputStream(bytes)
                    )
                }
                else -> {
                    val virtualBytes = state.virtualFileBytes(relative)
                        ?: (if (relative.startsWith("assets/")) state.virtualFileBytes(relative.removePrefix("assets/")) else state.virtualFileBytes("assets/$relative"))
                    if (virtualBytes != null) {
                        val mime = projectTextMimeType(relative)
                        return WebResourceResponse(
                            mime,
                            "UTF-8",
                            200,
                            "OK",
                            mapOf("Content-Length" to virtualBytes.size.toString(), "Cache-Control" to "no-store"),
                            ByteArrayInputStream(virtualBytes)
                        )
                    }
                    val name = if (relative in state.assets) relative else relative.removePrefix("assets/")
                    val asset = state.assets[relative]
                        ?: state.assets[relative.removePrefix("assets/")]
                        ?: state.assets["assets/$relative"]
                        ?: return missing()
                    if (!validAssetName(name) && !validAssetName(relative)) return missing()
                    if (!storage.contains(asset)) return missing()
                    val rangeHeader = request.requestHeaders.entries.firstOrNull { it.key.equals("Range", true) }?.value
                    val range = assetByteRange(rangeHeader, asset.size)
                    if (rangeHeader != null && range == null) {
                        return WebResourceResponse(
                            asset.mime,
                            null,
                            416,
                            "Range Not Satisfiable",
                            mapOf("Content-Range" to "bytes */${asset.size}"),
                            ByteArrayInputStream(byteArrayOf())
                        )
                    }
                    val start = range?.first ?: 0L
                    val length = range?.let { it.last - it.first + 1 } ?: asset.size
                    val stream = storage.file(asset).inputStream()
                    try {
                        stream.channel.position(start)
                        val headers = mutableMapOf("Content-Length" to length.toString(), "Accept-Ranges" to "bytes", "Cache-Control" to "no-store")
                        if (range != null) headers["Content-Range"] = "bytes $start-${range.last}/${asset.size}"
                        WebResourceResponse(
                            asset.mime,
                            null,
                            if (range == null) 200 else 206,
                            if (range == null) "OK" else "Partial Content",
                            headers,
                            LimitedAssetStream(stream, length)
                        )
                    } catch (e: Exception) {
                        runCatching { stream.close() }
                        throw e
                    }
                }
            }
        } catch (_: Exception) { missing() }
    }

    private fun missing() = WebResourceResponse(
        "text/plain",
        "UTF-8",
        404,
        "Not Found",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream("Asset not found".toByteArray())
    )

    companion object {
        private val BUNDLED_ASSET_CACHE = ConcurrentHashMap<String, ByteArray>()
    }
}
