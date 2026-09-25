package com.hikariatelier.app

import androidx.compose.foundation.shape.RoundedCornerShape

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale

internal fun recordingThumbnail(file: File, mime: String): Bitmap? = runCatching {
    if (mime == "image/gif") {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 480) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        BitmapFactory.decodeFile(file.path, options)
    } else {
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(file.path)
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                reader.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 480)
            } else null
        } finally { reader.release() }
    }
}.getOrNull()

private fun recordingSize(bytes: Long) =
    String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RecordingOptionsSheet(
    bitrate: Int, countdown: Int, text: (String) -> String,
    onBitrate: (Int) -> Unit, onCountdown: (Int) -> Unit,
    onDismiss: () -> Unit, onStart: (String) -> Unit
) {
    var format by rememberSaveable { mutableStateOf("mp4") }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text("録画設定"), style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("mp4", "gif").forEach {
                    FilterChip(selected = format == it, onClick = { format = it },
                        label = { Text(it.uppercase()) })
                }
            }
            if (format == "mp4") {
                Text(text("MP4ビットレート"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 5, 10).forEach {
                        FilterChip(selected = bitrate == it, onClick = { onBitrate(it) },
                            label = { Text("$it Mbps") })
                    }
                }
                Text(text("最大60秒の推定容量") + ": " + recordingSize(bitrate * 1_000_000L * 60 / 8),
                    style = MaterialTheme.typography.bodyMedium)
                Text(text("容量は映像の内容や端末により変わります"),
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text(text("GIFは30fps・最大15秒。容量は映像によって変わります"))
            }
            Text(text("録画開始カウントダウン"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 3, 5).forEach {
                    FilterChip(selected = countdown == it, onClick = { onCountdown(it) },
                        label = { Text(if (it == 0) text("なし") else "$it s") })
                }
            }
            Button(shape = ButtonDefaults.shape, onClick = { onStart(format) }, modifier = Modifier.fillMaxWidth()) {
                Text(text("録画を開始"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SavedRecordingSheet(
    name: String, mimeType: String, sizeBytes: Long, durationMillis: Long,
    thumbnail: Bitmap?, text: (String) -> String, onOpen: () -> Unit,
    onShare: () -> Unit, onX: () -> Unit, onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val isImage = mimeType.startsWith("image/") && mimeType != "image/gif"
            val titleText = if (name.contains("Card")) text("シェアカード") else if (isImage) text("スクリーンショット") else text("保存した録画")
            Text(titleText, style = MaterialTheme.typography.titleLarge)
            thumbnail?.let {
                Image(it.asImageBitmap(), text("プレビュー"), Modifier.fillMaxWidth().height(160.dp),
                    contentScale = ContentScale.Fit)
            }
            Text(name, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(mimeType.substringAfter('/').uppercase())
                if (!isImage) {
                    Text(formatRecordingDuration(durationMillis))
                }
                Text(recordingSize(sizeBytes))
            }
            OutlinedButton(shape = ButtonDefaults.outlinedShape, onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(text("開く")) }
            OutlinedButton(shape = ButtonDefaults.outlinedShape, onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text(text("共有")) }
            Button(shape = ButtonDefaults.shape, onClick = onX, modifier = Modifier.fillMaxWidth()) { Text(text("Xで共有")) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecordingStatus(
    format: String, elapsed: Long, remaining: Long, saving: Boolean,
    text: (String) -> String, modifier: Modifier = Modifier, onStop: () -> Unit
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
        Row(Modifier.padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (saving) text("保存中") else format, style = MaterialTheme.typography.labelMedium)
                if (!saving) {
                    Text(formatRecordingDuration(elapsed), style = MaterialTheme.typography.labelMedium)
                    Text(text("残り時間") + " " + formatRecordingDuration(remaining),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            if (saving) CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
            else IconButton(onClick = onStop) {
                Icon(painterResource(R.drawable.ic_stop), text("録画を停止"),
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
