package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    colors: ColorScheme,
    textTranslator: (String, Array<out Any?>) -> String,
    headerAction: @Composable () -> Unit = {},
    minimal: Boolean = false,
    dismissEnabled: Boolean = true,
    windowSetup: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val currentDismissEnabled by rememberUpdatedState(dismissEnabled)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { currentDismissEnabled || it != SheetValue.Hidden }
    )
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = colors.surface,
        contentColor = colors.onSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(
            topStart = if (minimal) 8.dp else 20.dp,
            topEnd = if (minimal) 8.dp else 20.dp
        ),
        dragHandle = null
    ) {
        windowSetup()
        Column(
            Modifier
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title, color = colors.onSurface, style = MaterialTheme.typography.titleMedium,
                        fontFamily = if (minimal) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        subtitle, style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                headerAction()
                IconButton(onClick = onDismiss, enabled = dismissEnabled) {
                    Icon(
                        painterResource(R.drawable.ic_close), uiText("閉じる"),
                        Modifier.size(20.dp), tint = colors.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
            Column(Modifier.weight(1f, fill = false).padding(top = 12.dp)) {
                content()
            }
        }
    }
}

@Composable
internal fun ActionRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    colors: ColorScheme,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        destructive -> colors.error
        else -> colors.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (destructive) colors.error.copy(alpha = 0.04f) else colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = if (enabled) 0.7f else 0.35f))
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(iconRes), null, Modifier.size(22.dp), tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = tint,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint.copy(alpha = if (enabled) 0.7f else 0.6f)
                )
            }
        }
    }
}

@Composable
internal fun SectionLabel(
    text: String,
    colors: ColorScheme,
    isLandscape: Boolean
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(
            start = if (isLandscape) 10.dp else 12.dp,
            bottom = 4.dp
        )
    )
}

@Composable
internal fun WorkSelectorChip(
    activeWorkTitle: String?,
    hasUnsavedChanges: Boolean,
    isLandscape: Boolean,
    manualRotation: Boolean,
    colors: ColorScheme,
    textTranslator: (String, Array<out Any?>) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    saveLabel: String? = null,
    saveFailed: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    Surface(
        onClick = onClick,
        modifier = if (isLandscape) modifier.height(34.dp) else modifier,
        shape = RoundedCornerShape(if (isLandscape) 10.dp else 16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            Modifier
                .then(if (isLandscape) Modifier.fillMaxHeight() else Modifier)
                .padding(
                    horizontal = 10.dp,
                    vertical = if (isLandscape) 0.dp else 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasUnsavedChanges) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.tertiary)
                        )
                    }
                    Text(
                        activeWorkTitle ?: uiText("作品を選択"),
                        modifier = Modifier.weight(1f),
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    saveLabel?.let { Text(it, modifier = Modifier.widthIn(max = 60.dp),
                        color = if (saveFailed) colors.error else colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            } else {
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = if (manualRotation) 160.dp else 220.dp)
                ) {
                    Text(
                        saveLabel?.let { uiText("作品") + " · " + it } ?: if (hasUnsavedChanges) uiText("作品・未保存") else uiText("作品"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (saveFailed) colors.error else if (hasUnsavedChanges) colors.tertiary else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        activeWorkTitle ?: uiText("作品を選択"),
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            onRetry?.let { retry ->
                TextButton(onClick = retry, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)) { Text(uiText("再試行"), style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
internal fun WorkActionsMenu(
    activeWork: Work?,
    canUndo: Boolean,
    canRedo: Boolean,
    canDelete: Boolean,
    hasUnsavedChanges: Boolean,
    isLandscape: Boolean,
    manualRotation: Boolean,
    wideWorkPanels: Boolean,
    colors: ColorScheme,
    viewModel: WorkManagementViewModel,
    onRotate: () -> Unit,
    onOpenUserGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSearch: () -> Unit,
    onFormat: () -> Unit,
    onSnippets: () -> Unit,
    onSnapshot: () -> Unit,
    onHistory: () -> Unit,
    onAspectRatio: () -> Unit,
    onTogglePin: () -> Unit,
    onEditTags: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    assetBusy: Boolean,
    onExportZip: () -> Unit,
    onImportZip: () -> Unit,
    onImportJs: () -> Unit,
    onExportJs: () -> Unit,
    onDelete: () -> Unit,
    onOpenProjectFiles: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenRuntime: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    windowSetup: @Composable () -> Unit = {}
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    @Composable
    fun EditActions() {
        SectionLabel(uiText("編集・作品"), colors, isLandscape)
        ActionRow(
            iconRes = R.drawable.ic_undo,
            title = uiText("元に戻す"),
            subtitle = uiText("直前の編集を取り消す"),
            colors = colors,
            enabled = canUndo,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onUndo()
            }
        )
        ActionRow(
            iconRes = R.drawable.ic_redo,
            title = uiText("やり直す"),
            subtitle = uiText("取り消した編集をやり直す"),
            colors = colors,
            enabled = canRedo,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onRedo()
            }
        )
        ActionRow(
            iconRes = R.drawable.ic_search,
            title = uiText("検索・置換"),
            subtitle = uiText("文字列検索、置換、指定行へ移動"),
            colors = colors,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onSearch()
            }
        )
        ActionRow(iconRes = R.drawable.ic_snippet, title = uiText("スニペット"),
            subtitle = uiText("カーソル位置に定番コードを挿入"), colors = colors,
            onClick = { viewModel.workActionsMenuExpanded = false; onSnippets() })
        ActionRow(
            iconRes = R.drawable.ic_format,
            title = uiText("コードを整形"),
            subtitle = uiText("インデントと空行を整理"),
            colors = colors,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onFormat()
            }
        )
        ActionRow(
            iconRes = R.drawable.ic_snapshot,
            title = uiText("スナップショット"),
            subtitle = uiText("状態の記録・復元"),
            colors = colors,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onSnapshot()
            }
        )
        ActionRow(
            iconRes = R.drawable.ic_history,
            title = uiText("変更履歴"),
            subtitle = uiText("過去の保存状態を表示・復元"),
            colors = colors,
            enabled = activeWork?.revisions?.isNotEmpty() == true,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onHistory()
            }
        )
        ActionRow(
            iconRes = R.drawable.ic_aspect_ratio,
            title = uiText("プレビュー比率"),
            subtitle = uiText("作品ごとにキャンバスの縦横比を設定"),
            colors = colors,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onAspectRatio()
            }
        )
        ActionRow(
            iconRes = R.drawable.ic_snippet,
            title = uiText("テンプレートとして保存"),
            subtitle = uiText("コード・素材・実行設定を新規作品のひな形にする"),
            colors = colors,
            enabled = activeWork != null && !assetBusy,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                viewModel.showSaveTemplateDialog = true
            }
        )
        ActionRow(iconRes = R.drawable.ic_snippet, title = uiText("テンプレート管理"),
            subtitle = uiText("自作テンプレートを検索・名前変更・更新"), colors = colors,
            enabled = !assetBusy,
            onClick = { viewModel.workActionsMenuExpanded = false; viewModel.showTemplateManager = true })
        val currentIsPinned = activeWork?.isPinned == true
        ActionRow(
            iconRes = if (currentIsPinned) R.drawable.ic_pin_filled else R.drawable.ic_pin,
            title = if (currentIsPinned) uiText("ピン留め解除") else uiText("ピン留め"),
            subtitle = if (currentIsPinned) uiText("ピン留めを解除して通常の並び順に戻す") else uiText("作品をピン留めして上部に固定"),
            colors = colors,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onTogglePin()
            }
        )
        ActionRow(
            iconRes = R.drawable.ic_tag,
            title = uiText("タグを編集"),
            subtitle = uiText("作品の分類タグを管理"),
            colors = colors,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onEditTags()
            }
        )
        ActionRow(
            iconRes = R.drawable.ic_rename,
            title = uiText("名前を変更"),
            subtitle = uiText("作品タイトルを編集"),
            colors = colors,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onRename()
            }
        )
        ActionRow(
            iconRes = R.drawable.ic_duplicate,
            title = uiText("複製"),
            subtitle = uiText("現在のコードからコピーを作成"),
            colors = colors,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onDuplicate()
            }
        )
    }

    @Composable
    fun FileActions() {
        TextButton(enabled = !assetBusy, onClick = {
            viewModel.workActionsMenuExpanded = false
            onExportZip()
        }) { Text(uiText("作品ZIPを書き出す")) }
        TextButton(enabled = !assetBusy, onClick = {
            viewModel.workActionsMenuExpanded = false
            onImportZip()
        }) { Text(uiText("作品ZIPを追加")) }

        SectionLabel(uiText("ファイル"), colors, isLandscape)
        ActionRow(
            iconRes = R.drawable.ic_import_js,
            title = uiText("JSをインポート"),
            subtitle = uiText("外部のJavaScriptファイルを読み込む"),
            colors = colors,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onImportJs()
            }
        )
        ActionRow(
            iconRes = R.drawable.ic_export_js,
            title = uiText("JSを書き出す"),
            subtitle = uiText("現在の作品を.jsファイルとして保存"),
            colors = colors,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onExportJs()
            }
        )
    }

    @Composable
    fun DeleteAction() {
        ActionRow(
            iconRes = R.drawable.ic_delete,
            title = uiText("削除"),
            subtitle = if (canDelete) uiText("この作品を削除") else uiText("最後の1作品は削除できません"),
            colors = colors,
            enabled = canDelete,
            destructive = true,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onDelete()
            }
        )
    }

    @Composable
    fun HelpAction() {
        SectionLabel(uiText("ヘルプ"), colors, isLandscape)
        ActionRow(
            iconRes = R.drawable.ic_help,
            title = uiText("使い方ガイド"),
            subtitle = uiText("操作方法や機能の解説"),
            colors = colors,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                onOpenUserGuide()
            }
        )
    }

    val actionButtonSize = if (isLandscape) 34.dp else 38.dp
    val actionIconSize = if (isLandscape) 18.dp else 20.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 2.dp else 4.dp)
    ) {
        if (manualRotation) {
            IconButton(
                onClick = onRotate,
                modifier = Modifier.size(actionButtonSize)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_rotate),
                    contentDescription = uiText("画面を回転"),
                    tint = colors.onSurface,
                    modifier = Modifier.size(actionIconSize)
                )
            }
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(actionButtonSize)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = uiText("設定"),
                tint = colors.onSurface,
                modifier = Modifier.size(actionIconSize)
            )
        }
        IconButton(
            enabled = activeWork != null,
            onClick = {
                viewModel.workActionsMenuExpanded = false
                viewModel.workSettingsMenuExpanded = true
            },
            modifier = Modifier.size(actionButtonSize)
        ) {
            Icon(
                painterResource(R.drawable.ic_folder_code),
                contentDescription = uiText("作品設定"),
                tint = colors.onSurface,
                modifier = Modifier.size(actionIconSize)
            )
        }
        IconButton(
            onClick = { viewModel.workActionsMenuExpanded = true },
            modifier = Modifier.size(actionButtonSize)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vertical),
                contentDescription = uiText("作品メニュー"),
                tint = colors.onSurface,
                modifier = Modifier.size(actionIconSize)
            )
        }
    }

    if (viewModel.workSettingsMenuExpanded) {
        WorkSheet(
            title = uiText("作品設定"),
            subtitle = activeWork?.title ?: uiText("作品未選択"),
            onDismiss = { viewModel.workSettingsMenuExpanded = false },
            colors = colors,
            textTranslator = textTranslator,
            windowSetup = windowSetup
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
            ) {
                ActionRow(
                    iconRes = R.drawable.ic_folder_code,
                    title = uiText("プロジェクトファイル"),
                    subtitle = uiText("追加JavaScriptファイルを管理"),
                    colors = colors,
                    onClick = {
                        viewModel.workSettingsMenuExpanded = false
                        onOpenProjectFiles()
                    }
                )
                ActionRow(
                    iconRes = R.drawable.ic_assets,
                    title = uiText("作品の素材"),
                    subtitle = uiText("画像・音声・フォントなどを管理"),
                    colors = colors,
                    onClick = {
                        viewModel.workSettingsMenuExpanded = false
                        onOpenAssets()
                    }
                )
                ActionRow(
                    iconRes = R.drawable.ic_terminal,
                    title = uiText("実行環境"),
                    subtitle = uiText("p5.jsとライブラリを作品ごとに設定"),
                    colors = colors,
                    onClick = {
                        viewModel.workSettingsMenuExpanded = false
                        onOpenRuntime()
                    }
                )
            }
        }
    }

    if (viewModel.workActionsMenuExpanded) {
        WorkSheet(
            title = uiText("作品メニュー"),
            subtitle = (activeWork?.title ?: uiText("作品未選択")) +
                if (hasUnsavedChanges) uiText(" · 未保存") else "",
            onDismiss = { viewModel.workActionsMenuExpanded = false },
            colors = colors,
            textTranslator = textTranslator,
            windowSetup = windowSetup
        ) {
            Row(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    EditActions()
                    if (!wideWorkPanels) {
                        Spacer(Modifier.height(12.dp))
                        FileActions()
                        Spacer(Modifier.height(12.dp))
                        HelpAction()
                        Spacer(Modifier.height(12.dp))
                        SectionLabel(uiText("管理"), colors, isLandscape)
                        DeleteAction()
                    }
                }
                if (wideWorkPanels) {
                    Column(Modifier.weight(1f)) {
                        FileActions()
                        Spacer(Modifier.height(12.dp))
                        HelpAction()
                        Spacer(Modifier.height(12.dp))
                        SectionLabel(uiText("管理"), colors, isLandscape)
                        DeleteAction()
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkGallerySheet(
    visible: Boolean,
    works: List<Work>,
    activeWorkId: String,
    hasUnsavedChanges: Boolean,
    initialSort: String,
    cacheDir: java.io.File,
    previewRevision: Int,
    updatedPreviewId: String?,
    assetBusy: Boolean,
    onSortChange: (String) -> Unit,
    onSelectWork: (Work, Boolean) -> Unit,
    onAddWork: () -> Unit,
    onDismiss: () -> Unit,
    onTogglePin: (Work) -> Unit,
    onEditTags: (Work) -> Unit,
    onDeleteGlobalTag: (String) -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    windowSetup: @Composable () -> Unit = {}
) {
    if (!visible) return

    WorkGallery(
        works = works,
        activeId = activeWorkId,
        unsaved = hasUnsavedChanges,
        sort = initialSort,
        cacheDir = cacheDir,
        previewRevision = previewRevision,
        updatedPreviewId = updatedPreviewId,
        text = { s -> textTranslator(s, emptyArray()) },
        onSort = onSortChange,
        onOpen = { work, openMenu ->
            if (!assetBusy) {
                onSelectWork(work, openMenu)
            }
        },
        onAdd = onAddWork,
        onDismiss = onDismiss,
        onTogglePin = onTogglePin,
        onEditTags = onEditTags,
        onDeleteGlobalTag = onDeleteGlobalTag,
        windowSetup = windowSetup
    )
}

@Composable
internal fun WorkBar(
    activeWorkTitle: String?,
    hasUnsavedChanges: Boolean,
    isLandscape: Boolean,
    manualRotation: Boolean,
    colors: ColorScheme,
    onOpenWorkMenu: () -> Unit,
    workActionsContent: @Composable () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            WorkSelectorChip(
                activeWorkTitle = activeWorkTitle,
                hasUnsavedChanges = hasUnsavedChanges,
                isLandscape = isLandscape,
                manualRotation = manualRotation,
                colors = colors,
                textTranslator = textTranslator,
                onClick = onOpenWorkMenu
            )
        }
        workActionsContent()
    }
}
