package com.hikariatelier.app

import android.content.res.AssetManager
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream

internal const val PREVIEW_ORIGIN = "https://appassets.androidplatform.net"
internal fun previewUrl(token: String) = "$PREVIEW_ORIGIN/project/$token/p5_runner.html"

internal data class PreviewAssets(val token: String, val assets: Map<String, ProjectAsset>)

/** Serves only the active preview's files under a local HTTPS origin. */
internal class AssetWebClient(
    private val bundled: AssetManager,
    private val storage: AssetStorage,
    private val snapshot: () -> PreviewAssets
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        request?.isForMainFrame == true && request.url.toString() != previewUrl(snapshot().token)

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val uri = request?.url ?: return null
        if (uri.scheme != "https" || uri.host != "appassets.androidplatform.net") return null
        val state = snapshot()
        val prefix = "/project/${state.token}/"
        if (!uri.path.orEmpty().startsWith(prefix)) return missing()
        val relative = uri.path.orEmpty().removePrefix(prefix)
        return try {
            when (relative) {
                "p5_runner.html" -> WebResourceResponse("text/html", "UTF-8", bundled.open("public/p5_runner.html"))
                "p5.min.js", "p5-v1.min.js", "p5-v2.min.js", "p5.sound.min.js" ->
                    WebResourceResponse("application/javascript", "UTF-8", bundled.open("public/$relative"))
                else -> {
                    if (!relative.startsWith("assets/")) return missing()
                    val name = relative.removePrefix("assets/")
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
