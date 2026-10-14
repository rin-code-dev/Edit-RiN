package com.hikariatelier.app

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

internal fun assetLoaderCode(name: String, asset: ProjectAsset): String {
    val path = org.json.JSONObject.quote("assets/$name")
    val loader = when {
        asset.mime.startsWith("image/") -> "loadImage"
        asset.mime.startsWith("audio/") -> "loadSound"
        asset.mime.startsWith("font/") -> "loadFont"
        name.endsWith(".json", true) -> "loadJSON"
        name.endsWith(".csv", true) -> "loadTable"
        asset.mime.startsWith("video/") -> "createVideo"
        else -> "loadStrings"
    }
    return "$loader($path)"
}

private fun isPreviewableTextAsset(name: String, mime: String): Boolean {
    if (mime.startsWith("text/") || mime == "application/json") return true
    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return ext in setOf("json", "csv", "txt", "md", "frag", "vert", "glsl", "obj", "mtl", "svg", "xml")
}

private fun isPreviewableFontAsset(name: String, mime: String): Boolean {
    if (mime.startsWith("font/")) return true
    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return ext in setOf("ttf", "otf", "ttc")
}

private fun formatDurationMillis(durationMs: Int): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

@Composable
internal fun AssetPreviewDialog(
    name: String,
    asset: ProjectAsset,
    file: File,
    text: (String) -> String,
    onClose: () -> Unit
) {
    var bitmap by remember(file.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var imageDimensions by remember(file.path) { mutableStateOf<Pair<Int, Int>?>(null) }
    var fontFamily by remember(file.path) { mutableStateOf<FontFamily?>(null) }
    var details by remember(file.path) { mutableStateOf("") }
    var player by remember(file.path) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file.path) { mutableStateOf(false) }
    var ready by remember(file.path) { mutableStateOf(false) }
    var audioDurationMs by remember(file.path) { mutableStateOf<Int?>(null) }

    DisposableEffect(file.path, asset.mime) {
        var activePlayer: MediaPlayer? = null
        if (asset.mime.startsWith("audio/")) {
            val media = MediaPlayer()
            activePlayer = media
            player = media
            runCatching {
                media.setDataSource(file.path)
                media.setOnPreparedListener { mp ->
                    ready = true
                    audioDurationMs = mp.duration.takeIf { it > 0 }
                }
                media.setOnCompletionListener { playing = false }
                media.setOnErrorListener { _, _, _ ->
                    details = text("この素材はプレビューできません")
                    ready = false
                    playing = false
                    true
                }
                media.prepareAsync()
            }.onFailure {
                details = text("この素材はプレビューできません")
                ready = false
            }
        }
        onDispose {
            player = null
            playing = false
            ready = false
            runCatching { activePlayer?.release() }
        }
    }

    LaunchedEffect(file.path, name, asset.mime) {
        bitmap = null
        imageDimensions = null
        fontFamily = null
        if (!asset.mime.startsWith("audio/")) {
            details = ""
        }
        when {
            asset.mime.startsWith("image/") -> {
                val decoded = withContext(Dispatchers.IO) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, options)
                    val rawW = options.outWidth
                    val rawH = options.outHeight
                    options.inSampleSize = 1
                    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1024) {
                        options.inSampleSize *= 2
                    }
                    options.inJustDecodeBounds = false
                    val bmp = runCatching { BitmapFactory.decodeFile(file.path, options) }.getOrNull()
                    bmp to (if (rawW > 0 && rawH > 0) (rawW to rawH) else null)
                }
                bitmap = decoded.first
                imageDimensions = decoded.second
                if (bitmap == null) details = text("この素材はプレビューできません")
            }
            asset.mime.startsWith("audio/") -> Unit
            isPreviewableFontAsset(name, asset.mime) -> {
                val loadedFont = withContext(Dispatchers.IO) {
                    runCatching { Typeface.createFromFile(file) }.getOrNull()?.let { FontFamily(it) }
                }
                fontFamily = loadedFont
                if (loadedFont == null) details = text("この素材はプレビューできません")
            }
            isPreviewableTextAsset(name, asset.mime) -> {
                details = withContext(Dispatchers.IO) {
                    runCatching {
                        file.reader().use { reader ->
                            val chars = CharArray(4096)
                            val n = reader.read(chars)
                            if (n > 0) String(chars, 0, n) else ""
                        }
                    }.getOrDefault("")
                }
            }
            else -> details = text("この素材はプレビューできません")
        }
    }

    EditSettingsDialog(
        onDismissRequest = onClose,
        title = { Text(name) },
        text = {
            Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(asset.mime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                imageDimensions?.let { (w, h) ->
                    Text(
                        String.format(Locale.ROOT, text("解像度: %s × %s px"), w.toString(), h.toString()),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                bitmap?.let { Image(it.asImageBitmap(), name, Modifier.fillMaxWidth().heightIn(max = 280.dp)) }
                if (asset.mime.startsWith("audio/")) {
                    audioDurationMs?.let { ms ->
                        Text(
                            String.format(Locale.ROOT, text("再生時間: %s"), formatDurationMillis(ms)),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    TextButton(enabled = ready, onClick = {
                        if (playing) player?.pause() else player?.start()
                        playing = !playing
                    }) { Text(text(if (playing) "一時停止" else "再生")) }
                }
                fontFamily?.let { previewFont ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("ABCDEFGHIJKLM / abcdefghijklm", fontFamily = previewFont, fontSize = 18.sp)
                            Text("0123456789 !@#$%^&*()", fontFamily = previewFont, fontSize = 16.sp)
                            Text("あいうえお かきくけこ 漢字", fontFamily = previewFont, fontSize = 16.sp)
                        }
                    }
                }
                if (details.isNotEmpty()) {
                    Text(details, fontFamily = if (isPreviewableTextAsset(name, asset.mime)) FontFamily.Monospace else FontFamily.Default)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(text("閉じる")) } }
    )
}
