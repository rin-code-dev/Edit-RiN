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
    if (mime.startsWith("image/")) {
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
            reader.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 480)
        } finally { reader.release() }
    }
}.getOrNull()

internal fun imageThumbnail(bytes: ByteArray): Bitmap? = runCatching {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 480) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}.getOrNull()

private fun recordingSize(bytes: Long) =
    String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RecordingOptionsSheet(
    bitrate: Int, countdown: Int, text: (String) -> String,
    onBitrate: (Int) -> Unit, onCountdown: (Int) -> Unit,
    onDismiss: () -> Unit, onStart: (String, Int, Boolean) -> Unit
) {
    var format by rememberSaveable { mutableStateOf("mp4") }
    var durationSeconds by rememberSaveable { mutableIntStateOf(0) }
    var frameSync by rememberSaveable { mutableStateOf(false) }
    val landscape = isLandscapeLayout()
    val durationChoices = if (format == "gif") listOf(0, 3, 6, 10, 15) else listOf(0, 3, 6, 10, 15, 30)
    LaunchedEffect(format) {
        if (format != "gif") frameSync = false
        if (durationSeconds !in durationChoices) durationSeconds = 0
    }
    UnitModal(
        onDismissRequest = onDismiss,
        maxWidth = adaptiveSheetMaxWidth(),
        maxHeight = adaptiveSheetMaxHeight(),
        windowSetup = { KeepLandscapeDialogImmersive() }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = if (landscape) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (landscape) 8.dp else 12.dp)) {
            Text(text("録画設定"), style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("mp4", "gif").forEach {
                    FilterChip(
                        selected = format == it,
                        onClick = { format = it },
                        shape = UNIT_CONTROL_SHAPE,
                        colors = unitFilterChipColors(),
                        border = unitFilterChipBorder(format == it),
                        label = { Text(it.uppercase()) }
                    )
                }
            }
            Text(text("自動停止（ループ録画）"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                durationChoices.forEach { sec ->
                    FilterChip(
                        selected = durationSeconds == sec,
                        onClick = { durationSeconds = sec },
                        shape = UNIT_CONTROL_SHAPE,
                        colors = unitFilterChipColors(),
                        border = unitFilterChipBorder(durationSeconds == sec),
                        label = { Text(if (sec == 0) text("手動停止") else "${sec}s") }
                    )
                }
            }
            if (format == "mp4") {
                Text(text("MP4ビットレート"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 5, 10).forEach {
                        FilterChip(
                            selected = bitrate == it,
                            onClick = { onBitrate(it) },
                            shape = UNIT_CONTROL_SHAPE,
                            colors = unitFilterChipColors(),
                            border = unitFilterChipBorder(bitrate == it),
                            label = { Text("$it Mbps") }
                        )
                    }
                }
                val estSeconds = if (durationSeconds > 0) durationSeconds else 60
                val estLabel = if (durationSeconds > 0) {
                    "${text("推定容量")} (${estSeconds}s)"
                } else {
                    text("最大60秒の推定容量")
                }
                Text("$estLabel: " + recordingSize(bitrate * 1_000_000L * estSeconds / 8),
                    style = MaterialTheme.typography.bodyMedium)
                Text(text("ファイルサイズは、映像の内容や端末によって変わります"),
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(text("フレーム同期キャプチャ（コマ落ち防止）"), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text("重い3Dやブラシ作品でも1フレームずつ確実に描画して30fps GIFを生成します"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = frameSync,
                        onCheckedChange = { enabled ->
                            frameSync = enabled
                            if (enabled && durationSeconds == 0) durationSeconds = 3
                        }
                    )
                }
                val effectiveGifSec = if (durationSeconds > 0) durationSeconds else 15
                Text(
                    if (frameSync) "${effectiveGifSec}s · ${effectiveGifSec * 30} frames (30fps)"
                    else text("GIFは30fpsで、最大15秒まで保存できます。ファイルサイズは映像の内容によって変わります"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(text("録画開始カウントダウン"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 3, 5).forEach {
                    FilterChip(
                        selected = countdown == it,
                        onClick = { onCountdown(it) },
                        shape = UNIT_CONTROL_SHAPE,
                        colors = unitFilterChipColors(),
                        border = unitFilterChipBorder(countdown == it),
                        label = { Text(if (it == 0) text("なし") else "$it s") }
                    )
                }
            }
            Button(shape = UNIT_CONTROL_SHAPE, onClick = { onStart(format, durationSeconds, frameSync) }, modifier = Modifier.fillMaxWidth()) {
                Text(text("録画を開始"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordingRecoverySheet(
    pending: PendingRecording,
    saving: Boolean,
    text: (String) -> String,
    onRetry: () -> Unit,
    onChooseDestination: () -> Unit,
    onDiscard: () -> Unit
) {
    var confirmDiscard by rememberSaveable(pending.displayName) { mutableStateOf(false) }
    if (confirmDiscard) {
        UnitAlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(text("保存待ちの録画を破棄しますか？")) },
            text = { Text(text("この録画はまだ保存されていません。破棄すると復元できません。")) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }, shape = UNIT_CONTROL_SHAPE) { Text(text("破棄")) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }, shape = UNIT_CONTROL_SHAPE) { Text(text("キャンセル")) } }
        )
    }
    val landscape = isLandscapeLayout()
    UnitModal(
        onDismissRequest = {},
        dismissEnabled = false,
        maxWidth = adaptiveSheetMaxWidth(),
        maxHeight = adaptiveSheetMaxHeight(),
        windowSetup = { KeepLandscapeDialogImmersive() }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = if (landscape) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (landscape) 8.dp else 12.dp)) {
            Text(text("録画を保存できませんでした"), style = MaterialTheme.typography.titleLarge)
            Text(text("録画は残っています。もう一度保存するか、別の保存先を選んでください。"),
                style = MaterialTheme.typography.bodyMedium)
            Text(pending.displayName, style = MaterialTheme.typography.bodySmall)
            Text("${pending.mimeType.substringAfter('/').uppercase()} · ${formatRecordingDuration(pending.durationMillis)} · ${recordingSize(pending.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall)
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(shape = UNIT_CONTROL_SHAPE, onClick = onRetry, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text(text("もう一度保存"))
            }
            OutlinedButton(shape = UNIT_CONTROL_SHAPE, onClick = onChooseDestination, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text(text("別の保存先を選ぶ"))
            }
            TextButton(onClick = { confirmDiscard = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text(text("録画を破棄"), color = MaterialTheme.colorScheme.error)
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
    val landscape = isLandscapeLayout()
    UnitModal(
        onDismissRequest = onDismiss,
        maxWidth = adaptiveSheetMaxWidth(),
        maxHeight = adaptiveSheetMaxHeight(),
        windowSetup = { KeepLandscapeDialogImmersive() }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = if (landscape) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (landscape) 8.dp else 12.dp)) {
            val isImage = mimeType.startsWith("image/") && mimeType != "image/gif"
            val titleText = if (isImage) text("スクリーンショット") else text("保存した録画")
            Text(titleText, style = MaterialTheme.typography.titleLarge)
            thumbnail?.let {
                Image(it.asImageBitmap(), text("プレビュー"),
                    Modifier.fillMaxWidth().height(if (landscape) 104.dp else 160.dp),
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
            OutlinedButton(shape = UNIT_CONTROL_SHAPE, onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(text("開く")) }
            OutlinedButton(shape = UNIT_CONTROL_SHAPE, onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text(text("共有")) }
            Button(shape = UNIT_CONTROL_SHAPE, onClick = onX, modifier = Modifier.fillMaxWidth()) { Text(text("Xで共有")) }
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
            else IconButton(onClick = onStop, shape = UNIT_CONTROL_SHAPE) {
                Icon(painterResource(R.drawable.ic_stop), text("録画を停止"),
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
