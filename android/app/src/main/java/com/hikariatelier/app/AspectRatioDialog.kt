package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AspectRatioDialog(
    visible: Boolean,
    wideWorkPanels: Boolean,
    isLandscape: Boolean,
    devicePreviewRatio: Float,
    previewRatioSelection: String,
    codeFontFamily: FontFamily,
    uiText: (String) -> String,
    onSelectRatio: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val colors = MaterialTheme.colorScheme
    val configuration = LocalConfiguration.current
    val aspectColumns = if (wideWorkPanels) 3 else if (configuration.fontScale > 1.5f) 1 else 2
    val deviceRatioText = if (devicePreviewRatio >= 1f) {
        String.format(java.util.Locale.ROOT, "%.2f:1", devicePreviewRatio)
    } else {
        String.format(java.util.Locale.ROOT, "1:%.2f", 1f / devicePreviewRatio)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.onSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null
    ) {
        Column(
            Modifier
                .heightIn(max = (configuration.screenHeightDp * 0.85f).dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        uiText("プレビュー比率"),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        uiText("作品の表示枠を選択"),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        uiText("閉じる"),
                        Modifier.size(20.dp),
                        tint = colors.onSurfaceVariant
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PREVIEW_ASPECT_RATIOS.chunked(aspectColumns).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { ratio ->
                            val selected = previewRatioSelection == ratio
                            val ratioValue = previewAspectRatioValue(ratio, devicePreviewRatio)
                            Surface(
                                onClick = {
                                    onSelectRatio(ratio)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 84.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) colors.primary.copy(alpha = 0.08f) else colors.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) colors.primary else colors.outlineVariant
                                )
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth().height(28.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        val previewShape = fitPreviewSize(42f, 26f, ratioValue)
                                        Box(
                                            Modifier.size(previewShape.width.dp, previewShape.height.dp)
                                                .border(
                                                    1.dp,
                                                    if (selected) colors.primary else colors.onSurfaceVariant,
                                                    RoundedCornerShape(4.dp)
                                                )
                                        )
                                    }
                                    Text(
                                        if (ratio == "device") uiText("端末") else ratio,
                                        fontFamily = codeFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) colors.primary else colors.onSurface
                                    )
                                    Text(
                                        if (ratio == "device") deviceRatioText else when (ratio) {
                                            "1:1" -> uiText("正方形")
                                            "4:3" -> uiText("標準・横")
                                            "16:9" -> uiText("ワイド")
                                            else -> uiText("縦長")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        repeat(aspectColumns - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Text(
                    uiText("端末の向きに合わせて比率が切り替わります"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
    }
}
