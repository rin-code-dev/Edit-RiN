package com.hikariatelier.app

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

@Composable
internal fun AssetPreviewDialog(name: String, asset: ProjectAsset, file: File,
    text: (String) -> String, onClose: () -> Unit) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var details by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(file) {
        if (asset.mime.startsWith("image/")) {
            bitmap = withContext(Dispatchers.IO) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, options)
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1024) options.inSampleSize *= 2
                options.inJustDecodeBounds = false
                runCatching { BitmapFactory.decodeFile(file.path, options) }.getOrNull()
            }
            if (bitmap == null) details = text("この素材はプレビューできません")
        } else if (asset.mime.startsWith("audio/")) {
            val media = MediaPlayer()
            player = media
            runCatching {
                media.setDataSource(file.path)
                media.setOnPreparedListener { ready = true }
                media.setOnCompletionListener { playing = false }
                media.setOnErrorListener { _, _, _ -> details = text("この素材はプレビューできません"); ready = false; true }
                media.prepareAsync()
            }.onFailure { details = text("この素材はプレビューできません") }
        } else if (asset.mime.startsWith("text/") || asset.mime == "application/json") {
            details = withContext(Dispatchers.IO) { runCatching {
                file.reader().use { reader -> val chars = CharArray(4096); val n = reader.read(chars); if (n > 0) String(chars, 0, n) else "" }
            }.getOrDefault("") }
        } else details = text("この素材はプレビューできません")
    }
    DisposableEffect(file) { onDispose { player?.release() } }
    EditSettingsDialog(onDismissRequest = onClose, title = { Text(name) },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Text(asset.mime)
            bitmap?.let { Image(it.asImageBitmap(), name, Modifier.fillMaxWidth().heightIn(max = 280.dp)) }
            if (asset.mime.startsWith("audio/")) TextButton(enabled = ready, onClick = {
                if (playing) player?.pause() else player?.start()
                playing = !playing
            }) { Text(text(if (playing) "一時停止" else "再生")) }
            Text(details)
        } }, confirmButton = { TextButton(onClick = onClose) { Text(text("閉じる")) } })
}
