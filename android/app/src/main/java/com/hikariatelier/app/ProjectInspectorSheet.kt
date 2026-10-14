package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class InspectorTab {
    FILES_AND_ASSETS,
    RUNTIME_AND_RATIO,
    HISTORY_AND_SNAPSHOTS
}

/**
 * Unified 3-tab Project Inspector Sheet replacing scattered per-feature dialogs:
 * 1) Files & Assets
 * 2) Runtime (p5 version, Sound, Addon libraries, Aspect Ratio, Auto-Run)
 * 3) History & Snapshots
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectInspectorSheet(
    work: Work?,
    autoRunEnabled: Boolean,
    onToggleAutoRun: (Boolean) -> Unit,
    onOpenProjectFilesDialog: () -> Unit,
    onOpenAssetManagerDialog: () -> Unit,
    onOpenRuntimeDialog: () -> Unit,
    onOpenAspectRatioDialog: () -> Unit,
    onOpenSnapshotSheet: () -> Unit,
    onOpenHistoryDialog: () -> Unit,
    onOpenTagsDialog: () -> Unit,
    onSelectAspectRatio: (String) -> Unit,
    onDismiss: () -> Unit,
    codeFontFamily: FontFamily,
    textTranslator: (String, Array<out Any?>) -> String
) {
    var selectedTab by rememberSaveable { mutableStateOf(InspectorTab.FILES_AND_ASSETS) }
    val colors = MaterialTheme.colorScheme
    fun uiText(source: String, vararg args: Any?): String = textTranslator(source, args)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = work?.title ?: uiText("プロジェクト設定"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiText("ファイル・実行環境・履歴を一括管理"),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = onOpenTagsDialog,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tag),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(uiText("タグ"), style = MaterialTheme.typography.labelMedium)
                }
            }

            SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == InspectorTab.FILES_AND_ASSETS,
                    onClick = { selectedTab = InspectorTab.FILES_AND_ASSETS },
                    text = { Text(uiText("ファイル・素材"), maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == InspectorTab.RUNTIME_AND_RATIO,
                    onClick = { selectedTab = InspectorTab.RUNTIME_AND_RATIO },
                    text = { Text(uiText("実行環境・比率"), maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == InspectorTab.HISTORY_AND_SNAPSHOTS,
                    onClick = { selectedTab = InspectorTab.HISTORY_AND_SNAPSHOTS },
                    text = { Text(uiText("履歴・保護"), maxLines = 1) }
                )
            }

            when (selectedTab) {
                InspectorTab.FILES_AND_ASSETS -> {
                    val fileCount = 1 + (work?.files?.size ?: 0)
                    val assetCount = work?.assets?.size ?: 0
                    InspectorCardRow(
                        title = uiText("コード・シェーダーファイル (%s)", fileCount),
                        subtitle = (listOf("sketch.js") + work?.files.orEmpty().keys.sorted()).joinToString(", "),
                        actionLabel = uiText("管理"),
                        onClick = {
                            onDismiss()
                            onOpenProjectFilesDialog()
                        }
                    )
                    InspectorCardRow(
                        title = uiText("画像・フォント・音声素材 (%s)", assetCount),
                        subtitle = if (assetCount == 0) uiText("素材はまだ追加されていません")
                        else work?.assets.orEmpty().keys.sorted().joinToString(", "),
                        actionLabel = uiText("素材管理"),
                        onClick = {
                            onDismiss()
                            onOpenAssetManagerDialog()
                        }
                    )
                }

                InspectorTab.RUNTIME_AND_RATIO -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = uiText("Auto-Run（ライブ自動反映）"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = uiText("入力停止時にページリロードなしで自動実行"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoRunEnabled,
                                onCheckedChange = onToggleAutoRun
                            )
                        }
                    }

                    InspectorCardRow(
                        title = uiText("p5.js バージョン・アドオン"),
                        subtitle = "p5.js ${work?.p5Version ?: P5_VERSION_CURRENT}" +
                            (if (work?.p5SoundEnabled == true) " + p5.sound" else "") +
                            (if (work?.libraries.orEmpty().isNotEmpty()) " + ${work?.libraries.orEmpty().keys.joinToString()}" else ""),
                        actionLabel = uiText("変更"),
                        onClick = {
                            onDismiss()
                            onOpenRuntimeDialog()
                        }
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = uiText("プレビュー比率"),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("1:1", "4:3", "16:9", "9:16", "device").forEach { ratio ->
                                val selected = work?.previewAspectRatio == ratio
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectAspectRatio(ratio) },
                                    label = {
                                        Text(
                                            text = if (ratio == "device") uiText("端末") else ratio,
                                            fontFamily = codeFontFamily,
                                            fontSize = 11.sp
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                InspectorTab.HISTORY_AND_SNAPSHOTS -> {
                    val revCount = work?.revisions?.size ?: 0
                    InspectorCardRow(
                        title = uiText("スナップショット（名前付き保存）"),
                        subtitle = uiText("重要な節目を手動・自動で保護"),
                        actionLabel = uiText("開く"),
                        onClick = {
                            onDismiss()
                            onOpenSnapshotSheet()
                        }
                    )
                    InspectorCardRow(
                        title = uiText("最近の保存履歴 (%s)", revCount),
                        subtitle = uiText("直前の保存状態との差分を確認して復元"),
                        actionLabel = uiText("差分を見る"),
                        onClick = {
                            onDismiss()
                            onOpenHistoryDialog()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorCardRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2
                )
            }
            FilledTonalButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
