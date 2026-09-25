package com.hikariatelier.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareCardSheet(
    artwork: Bitmap,
    workTitle: String,
    fullCode: String,
    hasAssets: Boolean,
    initialAuthor: String = "",
    onAuthorChange: (String) -> Unit = {},
    initialSelectedRange: Pair<Int, Int>? = null,
    text: (String) -> String,
    onSave: (Bitmap) -> Unit,
    onShare: (Bitmap, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val lines = remember(fullCode) { fullCode.lines() }
    val totalLines = lines.size.coerceAtLeast(1)

    var author by remember(initialAuthor) { mutableStateOf(initialAuthor) }
    var selectedTheme by remember { mutableStateOf(ShareCardTheme.DARK) }

    val qrStatus = remember(fullCode, hasAssets) {
        ShareCardGenerator.checkQrStatus(fullCode, hasAssets)
    }

    var includeQr by remember(qrStatus) {
        mutableStateOf(qrStatus == QrStatus.AVAILABLE)
    }

    var allowWebCodeView by remember { mutableStateOf(true) }

    var includeCode by remember { mutableStateOf(true) }

    var startLine by remember(initialSelectedRange, totalLines) {
        mutableIntStateOf(initialSelectedRange?.first?.coerceIn(1, totalLines) ?: 1)
    }
    var endLine by remember(initialSelectedRange, totalLines) {
        mutableIntStateOf(
            initialSelectedRange?.second?.coerceIn(startLine, totalLines)
                ?: minOf(12, totalLines)
        )
    }

    // Reactive card bitmap generated on background thread
    val cardBitmap by produceState<Bitmap?>(
        initialValue = null,
        artwork,
        workTitle,
        author,
        selectedTheme,
        fullCode,
        includeCode,
        startLine,
        endLine,
        includeQr,
        qrStatus,
        allowWebCodeView
    ) {
        value = withContext(Dispatchers.Default) {
            val snippet = if (includeCode && lines.isNotEmpty()) {
                val sIdx = (startLine - 1).coerceIn(0, lines.size - 1)
                val eIdx = endLine.coerceIn(sIdx + 1, lines.size)
                lines.subList(sIdx, eIdx).joinToString("\n")
            } else ""

            ShareCardGenerator.renderShareCard(
                artwork = artwork,
                config = ShareCardConfig(
                    title = workTitle,
                    author = author,
                    fullCode = fullCode,
                    theme = selectedTheme,
                    includeCode = includeCode,
                    snippetCode = snippet,
                    snippetStartLine = startLine,
                    includeQr = includeQr,
                    qrStatus = qrStatus,
                    allowWebCodeView = allowWebCodeView
                )
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text("シェアカード設定"), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = text("全画面表示を閉じる"))
                }
            }

            // Live Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF101014))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                cardBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = text("プレビュー"),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } ?: CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
            }

            // Theme Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text("テーマ"), style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShareCardTheme.values().forEach { theme ->
                            val isSelected = selectedTheme == theme
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTheme = theme },
                                label = {
                                    Text(
                                        when (theme) {
                                            ShareCardTheme.DARK -> text("ダーク")
                                            ShareCardTheme.MIDNIGHT -> text("ミッドナイト")
                                            ShareCardTheme.CYBER -> text("サイバー")
                                            ShareCardTheme.LIGHT -> text("ライト")
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Author Credit Input
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text("作者クレジット（任意）"), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = author,
                        onValueChange = {
                            author = it
                            onAuthorChange(it)
                        },
                        placeholder = { Text(text("例: @username")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // QR Code Setting / Status Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (qrStatus) {
                        QrStatus.AVAILABLE -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (qrStatus) {
                        QrStatus.AVAILABLE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text("QRコードを含める"),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text("カメラ等でスキャンしてブラウザで作品を実行できます"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = includeQr,
                                    onCheckedChange = { includeQr = it }
                                )
                            }

                            if (includeQr) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text("Webでコードの閲覧・コピーを許可"),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text("OFFにすると、Web閲覧者はコードを見たりコピーしたりできなくなります"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = allowWebCodeView,
                                        onCheckedChange = { allowWebCodeView = it }
                                    )
                                }
                            }
                        }
                        QrStatus.CONTAINS_ASSETS -> {
                            Text(
                                text("⚠️ 作品に画像・音声などの外部素材が含まれているため、QRコードでのWeb実行は利用できません。プレビューとコードのみのカードを作成します。"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        QrStatus.TOO_LARGE -> {
                            Text(
                                text("⚠️ コード容量がQRコードの上限を超えているため、QRコードを含められません。プレビューとコードのみのカードを作成します。"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Source Code Toggle & Range Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text("ソースコードを掲載"),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text("カード内にコードスニペットを表示します"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = includeCode,
                            onCheckedChange = { includeCode = it }
                        )
                    }

                    if (includeCode) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                String.format(
                                    text("掲載範囲: %d 〜 %d 行目 (全 %d 行)"),
                                    startLine, endLine, totalLines
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        if (totalLines > 1) {
                            RangeSlider(
                                value = startLine.toFloat()..endLine.toFloat(),
                                onValueChange = { range ->
                                    val s = range.start.toInt().coerceIn(1, totalLines)
                                    val e = range.endInclusive.toInt().coerceIn(s, totalLines)
                                    startLine = s
                                    endLine = e
                                },
                                valueRange = 1f..totalLines.toFloat(),
                                steps = (totalLines - 2).coerceAtLeast(0)
                            )
                        }

                        // Fine adjustment buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text("開始:"), style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(
                                    onClick = { if (startLine > 1) startLine-- },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 32.dp)
                                ) { Text("-") }
                                OutlinedButton(
                                    onClick = { if (startLine < endLine) startLine++ },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 32.dp)
                                ) { Text("+") }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text("終了:"), style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(
                                    onClick = { if (endLine > startLine) endLine-- },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 32.dp)
                                ) { Text("-") }
                                OutlinedButton(
                                    onClick = { if (endLine < totalLines) endLine++ },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 32.dp)
                                ) { Text("+") }
                            }
                        }
                    }
                }
            }

            // Action Buttons
            OutlinedButton(
                shape = ButtonDefaults.outlinedShape,
                onClick = { cardBitmap?.let(onSave) },
                enabled = cardBitmap != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text("カードを端末に保存"))
            }

            OutlinedButton(
                shape = ButtonDefaults.outlinedShape,
                onClick = { cardBitmap?.let { onShare(it, false) } },
                enabled = cardBitmap != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text("共有"))
            }

            Button(
                shape = ButtonDefaults.shape,
                onClick = { cardBitmap?.let { onShare(it, true) } },
                enabled = cardBitmap != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text("Xで共有"))
            }
        }
    }
}
