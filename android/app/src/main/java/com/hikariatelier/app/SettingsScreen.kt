package com.hikariatelier.app

import android.content.res.AssetManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.ConfigurationCompat
import com.hikariatelier.app.ui.theme.AppThemeMode

private data class SettingsCategoryMeta(
    val iconRes: Int,
    val title: String,
    val summary: String,
    val searchKeywords: String
)

private fun extractNoticeSection(rawText: String, filterIndex: Int): List<String> {
    if (filterIndex == 0) {
        return rawText.split("\n\n").filter { it.isNotBlank() }
    }
    val summaryStart = rawText.indexOf("# Third-party software")
    val gplPart = if (summaryStart > 0) rawText.substring(0, summaryStart) else rawText

    fun betweenMarkers(startMarker: String, vararg endMarkers: String): String {
        val start = rawText.indexOf(startMarker)
        if (start < 0) return ""
        val after = start + startMarker.length
        val end = endMarkers
            .map { rawText.indexOf(it, after) }
            .filter { it >= 0 }
            .minOrNull() ?: rawText.length
        return rawText.substring(start, end)
    }

    val filtered = when (filterIndex) {
        1 -> gplPart
        2 -> buildString {
            appendLine(betweenMarkers("## p5.js 1.11.5", "## Android runtime dependencies"))
            appendLine(betweenMarkers("--- p5-LGPL-2.1.txt ---", "--- p5-brush-MIT.txt ---"))
            appendLine(betweenMarkers("--- p5-sound-LGPL-2.1.txt ---", "--- p5-sound-v1-MIT.txt ---"))
            appendLine(betweenMarkers("--- p5-sound-v1-MIT.txt ---", "--- androidx."))
        }
        3 -> buildString {
            appendLine(betweenMarkers("## Blockly 13.4.0 and Acorn 8.19.0", "## p5.js 1.11.5"))
            appendLine(betweenMarkers("--- blockly-Apache-2.0.txt ---", "--- matter-js-MIT.txt ---"))
        }
        4 -> buildString {
            appendLine(betweenMarkers("## Blockly 13.4.0 and Acorn 8.19.0", "## p5.js 1.11.5"))
            appendLine(betweenMarkers("--- acorn-AUTHORS.txt ---", "--- acorn-MIT.txt ---"))
            appendLine(betweenMarkers("--- acorn-MIT.txt ---", "--- blockly-Apache-2.0.txt ---"))
        }
        5 -> buildString {
            appendLine(betweenMarkers("## Matter.js 0.20.0", "## Website fonts"))
            appendLine(betweenMarkers("--- matter-js-MIT.txt ---", "--- p5-LGPL-2.1.txt ---"))
        }
        else -> rawText
    }.ifBlank { rawText }

    return filtered.split("\n\n").filter { it.isNotBlank() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onSettingsChange: (SettingsUiState.() -> SettingsUiState) -> Unit,
    font: FontSettingsUiState,
    actions: SettingsScreenActions,
    folderName: String,
    assets: AssetManager,
    updateViewModel: UpdateViewModel,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    val onThemeModeChange: (AppThemeMode) -> Unit = { value -> onSettingsChange { copy(themeMode = value) } }
    val onXShareTextChange: (String) -> Unit = { value -> onSettingsChange { copy(xShareText = value.take(1000)) } }
    val codeFontFamily = font.family ?: FontFamily.Monospace
    val fontFeatures = if (state.fontLigatures) "'liga' 1, 'clig' 1, 'calt' 1" else "'liga' 0, 'clig' 0, 'calt' 0"

    fun uiText(source: String, vararg arguments: Any?): String =
        textTranslator(source, arguments)

    val colors = MaterialTheme.colorScheme

    // A sideways phone or a large font does not necessarily have room for a sidebar.
    val settingsConfiguration = LocalConfiguration.current
    val settingsLandscape = settingsConfiguration.screenWidthDp >= 640 &&
        settingsConfiguration.screenWidthDp / settingsConfiguration.fontScale >= 560

    var settingsTab by rememberSaveable { mutableIntStateOf(-1) }
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    var showLicenses by remember { mutableStateOf(false) }
    var showUserGuide by remember { mutableStateOf(false) }
    var showReleaseNotes by remember { mutableStateOf(false) }

    if (showReleaseNotes) {
        val deviceLanguage = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language ?: "en"
        ReleaseNotesDialog(
            language = resolveUiLanguage(state.appLanguage, deviceLanguage),
            onDismiss = { showReleaseNotes = false }
        )
    }
    if (showUserGuide) {
        Dialog(
            onDismissRequest = { showUserGuide = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            val deviceLanguage = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language ?: "en"
            UserGuideScreen(
                language = resolveUiLanguage(state.appLanguage, deviceLanguage),
                onClose = { showUserGuide = false },
                onStartPracticeTour = {
                    showUserGuide = false
                    actions.onShowPracticeTour()
                },
                onTryCodeInEditor = actions.onTryGuideCode?.let { handler ->
                    { title, code ->
                        showUserGuide = false
                        handler(title, code)
                    }
                }
            )
        }
    }
    if (showLicenses) {
        val rawNoticeText = remember {
            assets.open("licenses/THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
        }
        var licenseFilter by rememberSaveable { mutableIntStateOf(0) }
        val filterLabels = listOf(
            uiText("すべて"),
            "Edit:RiN (GPL-3.0)",
            "p5.js / p5.sound",
            "Blockly (Apache-2.0)",
            "Acorn (MIT)",
            "Matter.js (MIT)"
        )
        val paragraphs = remember(rawNoticeText, licenseFilter) {
            extractNoticeSection(rawNoticeText, licenseFilter)
        }
        EditSettingsDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(uiText("ライセンス情報")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(filterLabels) { index, label ->
                            FilterChip(
                                selected = licenseFilter == index,
                                onClick = { licenseFilter = index },
                                shape = UNIT_CONTROL_SHAPE,
                                colors = unitFilterChipColors(colors),
                                border = unitFilterChipBorder(licenseFilter == index, true, colors),
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { actions.openExternalUrl(GITHUB_REPO_URL) },
                        shape = UNIT_CONTROL_SHAPE,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(uiText("対応ソース (GitHub)"))
                    }
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(paragraphs) { paragraph ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    paragraph,
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text(uiText("閉じる")) }
            }
        )
    }

    val themeSummary = when (state.themeMode) {
        AppThemeMode.SYSTEM -> uiText("自動")
        AppThemeMode.DARK -> uiText("ダーク")
        AppThemeMode.LIGHT -> uiText("ライト")
        AppThemeMode.SUMI -> "Sumi"
        AppThemeMode.CUSTOM -> uiText("カスタム")
    }
    val languageSummary = when (state.appLanguage) {
        "ja" -> "日本語"
        "en" -> "English"
        "zh" -> "中文（简体）"
        else -> uiText("システムデフォルト")
    }
    val fontSummary = if (font.family == null) uiText("標準フォント") else font.name
    val runModeSummary = if (state.autoRun) uiText("作品切替時に自動実行") else uiText("手動実行")
    val wrapSummary = if (state.editorWordWrap) uiText("折り返しあり") else uiText("折り返しなし")
    val countdownSummary = if (state.recordingCountdownSeconds == 0) uiText("なし") else uiText("%s秒", state.recordingCountdownSeconds)

    val categories = buildList {
        add(SettingsCategoryMeta(
            iconRes = R.drawable.ic_settings,
            title = uiText("外観"),
            summary = "$themeSummary · $languageSummary · $fontSummary",
            searchKeywords = "外観 言語 日本語 English 中文 テーマ 自動 ダーク ライト Sumi カスタム 背景色 アクセントカラー フォント 連字 ステータスバー ナビゲーションバー ノッチ 画面の向き 固定 回転 theme language font ligatures status bar navigation notch rotation"
        ))
        add(SettingsCategoryMeta(
            iconRes = R.drawable.ic_tune,
            title = uiText("画面と実行"),
            summary = "$runModeSummary · PiP ${if (state.compactPreview) "ON" else "OFF"} · ${kotlin.math.round(state.landscapePreviewFraction * 100).toInt()}%",
            searchKeywords = "画面と実行 自動実行 PiP 小窓 サイズ調整バー 横画面 プレビュー幅 エディターを左 全画面 描画サイズ 比率 auto run pip resize split landscape fullscreen"
        ))
        add(SettingsCategoryMeta(
            iconRes = R.drawable.ic_code,
            title = uiText("エディター"),
            summary = "${state.editorFontSize.toInt()} sp · $wrapSummary · // ? ${if (state.inlineWatchEnabled) "ON" else "OFF"}",
            searchKeywords = "エディター 文字サイズ 自動インデント 行番号 折り返し 入力候補 補完 インライン変数ウォッチ 編集キー 移動キー 記号キー コンパクト font size indent line numbers word wrap completion inline watch accessory bar tab"
        ))
        add(SettingsCategoryMeta(
            iconRes = R.drawable.ic_folder_code,
            title = uiText("保存とデータ"),
            summary = folderName,
            searchKeywords = "保存 データ バックアップ ZIP 復元 下書き 未保存 自動保存 フォルダー サンプル JavaScript JS インポート 書き出す HTML p5.js Web Editor backup zip draft folder samples export"
        ))
        add(SettingsCategoryMeta(
            iconRes = R.drawable.ic_record,
            title = uiText("録画と共有"),
            summary = "MP4 ${state.mp4BitrateMbps} Mbps · $countdownSummary",
            searchKeywords = "録画 共有 MP4 ビットレート カウントダウン X Twitter 定型文 シェアカード 著者名 QR URL recording bitrate countdown share card qr"
        ))
        add(SettingsCategoryMeta(
            iconRes = R.drawable.ic_system_update,
            title = uiText("アプリ情報"),
            summary = "Edit:RiN v${BuildConfig.VERSION_NAME}",
            searchKeywords = "アプリ情報 About アップデート 更新履歴 What's New エンジン WebView p5.js Blockly Acorn Matter.js ライセンス 対応ソース 使い方ガイド 入門ツアー 実践ツアー GitHub X OFUSE 公式サイト update release notes engine licenses guide tour"
        ))
        if (BuildConfig.DEBUG) {
            add(SettingsCategoryMeta(
                iconRes = R.drawable.ic_terminal,
                title = uiText("デバッグ"),
                summary = uiText("デバッグAPK専用の検証項目"),
                searchKeywords = "デバッグ debug 実験 検証 build"
            ))
        }
    }

    // Portrait shows the category list (-1) first; landscape always shows a category.
    val tab = if (settingsLandscape) settingsTab.coerceIn(0, categories.lastIndex) else settingsTab
    if (!settingsLandscape && tab >= 0) {
        BackHandler { settingsTab = -1 }
    }
    val categoryScrolls = List(categories.size) { rememberScrollState() }
    val settingsScroll = categoryScrolls[tab.coerceAtLeast(0)]

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(if (settingsLandscape) 8.dp else 0.dp)
    ) {
        if (settingsLandscape) {
            Surface(
                modifier = Modifier.width(196.dp).fillMaxHeight(),
                color = colors.surface,
                contentColor = colors.onSurface,
                border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
                shape = UNIT_SHAPE
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = actions.onBack, shape = UNIT_CONTROL_SHAPE) {
                            Icon(painterResource(R.drawable.ic_back), uiText("戻る"), Modifier.size(20.dp))
                        }
                    }
                    Text(
                        uiText("設定"),
                        Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    categories.forEachIndexed { index, category ->
                        Surface(
                            onClick = { settingsTab = index },
                            modifier = Modifier.fillMaxWidth(),
                            shape = UNIT_CARD_SHAPE,
                            color = if (tab == index) colors.primary.copy(alpha = 0.1f) else colors.surface,
                            contentColor = colors.onSurface,
                            border = if (tab == index) BorderStroke(UNIT_BORDER_WIDTH, colors.primary) else null
                        ) {
                            Row(
                                Modifier.heightIn(min = 48.dp).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painterResource(category.iconRes),
                                    null,
                                    Modifier.size(18.dp),
                                    tint = if (tab == index) colors.primary else colors.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    category.title,
                                    modifier = Modifier.weight(1f),
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    Surface(
                        onClick = { showUserGuide = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = UNIT_CARD_SHAPE,
                        color = colors.surface,
                        contentColor = colors.onSurface
                    ) {
                        Row(
                            Modifier.heightIn(min = 48.dp).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_help),
                                null,
                                Modifier.size(18.dp),
                                tint = colors.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                uiText("使い方ガイド"),
                                modifier = Modifier.weight(1f),
                                color = colors.onSurface,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(settingsScroll)
                    .padding(
                        start = if (settingsLandscape) 12.dp else 16.dp,
                        top = 8.dp,
                        end = if (settingsLandscape) 8.dp else 16.dp,
                        bottom = 16.dp
                    ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!settingsLandscape) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (tab >= 0) settingsTab = -1 else actions.onBack() },
                        shape = UNIT_CONTROL_SHAPE
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = uiText("戻る"),
                            tint = colors.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (tab >= 0) categories[tab].title else uiText("設定"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Edit:RiN",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            if (!settingsLandscape && tab < 0) {
                OutlinedTextField(
                    value = settingsSearchQuery,
                    onValueChange = { settingsSearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = UNIT_CONTROL_SHAPE,
                    label = { Text(uiText("設定を検索")) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_search),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (settingsSearchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { settingsSearchQuery = "" }, shape = UNIT_CONTROL_SHAPE) {
                                Icon(
                                    painterResource(R.drawable.ic_clear),
                                    contentDescription = uiText("クリア"),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else null
                )

                val q = settingsSearchQuery.trim()
                val matchedCategories = categories.withIndex().filter { (_, category) ->
                    q.isBlank() ||
                        category.title.contains(q, ignoreCase = true) ||
                        category.summary.contains(q, ignoreCase = true) ||
                        category.searchKeywords.contains(q, ignoreCase = true)
                }
                val guideTitle = uiText("使い方ガイド")
                val guideSummary = uiText("エディターの操作や各機能の解説")
                val showGuideEntry = q.isBlank() ||
                    guideTitle.contains(q, ignoreCase = true) ||
                    guideSummary.contains(q, ignoreCase = true) ||
                    "guide help ヘルプ ガイド リファレンス 使い方 @rin @slider".contains(q, ignoreCase = true)

                if (matchedCategories.isEmpty() && !showGuideEntry) {
                    Text(
                        text = uiText("一致する設定項目がありません"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                    )
                } else {
                    matchedCategories.forEach { (index, category) ->
                        Surface(
                            onClick = { settingsTab = index },
                            modifier = Modifier.fillMaxWidth(),
                            shape = UNIT_CARD_SHAPE,
                            color = colors.surface,
                            contentColor = colors.onSurface,
                            border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant)
                        ) {
                            Row(
                                Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(painterResource(category.iconRes), null, Modifier.size(22.dp), tint = colors.primary)
                                Spacer(Modifier.width(14.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(category.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        category.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    "›",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }

                    if (showGuideEntry) {
                        Surface(
                            onClick = { showUserGuide = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = UNIT_CARD_SHAPE,
                            color = colors.surface,
                            contentColor = colors.onSurface,
                            border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant)
                        ) {
                            Row(
                                Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(painterResource(R.drawable.ic_help), null, Modifier.size(22.dp), tint = colors.primary)
                                Spacer(Modifier.width(14.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(guideTitle, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        guideSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    "›",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (tab == 0) {
                SettingsSection(
                    title = uiText("外観"),
                    description = uiText("テーマとシステムUI")
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            uiText("言語"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            uiText("対応していない端末言語の場合は英語を使用します"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        listOf(
                            "system" to uiText("システムデフォルト"),
                            "ja" to "日本語",
                            "en" to "English",
                            "zh" to "中文（简体）"
                        ).chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (code, label) ->
                                    FilterChip(
                                        selected = state.appLanguage == code,
                                        onClick = { onSettingsChange { copy(appLanguage = code) } },
                                        shape = UNIT_CONTROL_SHAPE,
                                        colors = unitFilterChipColors(colors),
                                        border = unitFilterChipBorder(state.appLanguage == code, true, colors),
                                        label = {
                                            AutoFitSingleLineText(
                                                text = label,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                minFontSize = 10.sp
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    SettingsDivider()
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = uiText("テーマ"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = when (state.themeMode) {
                                AppThemeMode.SYSTEM -> uiText("端末の設定とMaterial Youに合わせます")
                                AppThemeMode.DARK -> uiText("黒を基調とした表示")
                                AppThemeMode.SUMI -> uiText("墨色と生成り、朱色のアクセント")
                                AppThemeMode.LIGHT -> uiText("白を基調とした表示")
                                AppThemeMode.CUSTOM -> uiText("背景とアクセントカラーを設定")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )

                        Spacer(Modifier.height(14.dp))

                        val themeChoices = listOf(
                            AppThemeMode.SYSTEM to uiText("自動"),
                            AppThemeMode.LIGHT to uiText("ライト"),
                            AppThemeMode.SUMI to "Sumi",
                            AppThemeMode.DARK to uiText("ダーク"),
                            AppThemeMode.CUSTOM to uiText("カスタム")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            themeChoices.forEach { (mode, label) ->
                                ThemeSwatchCard(
                                    mode = mode,
                                    label = label,
                                    selected = state.themeMode == mode,
                                    customBackground = Color(state.customBackground),
                                    customAccent = Color(state.customAccent),
                                    onClick = { onThemeModeChange(mode) }
                                )
                            }
                        }
                    }

                    SettingsDivider()

                    if (state.themeMode == AppThemeMode.CUSTOM) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CustomColorSetting(
                                uiText("背景色"),
                                state.customBackground,
                                { onSettingsChange { copy(customBackground = it) } },
                                { s -> uiText(s) }
                            )
                            CustomColorSetting(
                                uiText("アクセントカラー"),
                                state.customAccent,
                                { onSettingsChange { copy(customAccent = it) } },
                                { s -> uiText(s) }
                            )
                        }
                        SettingsDivider()
                    }

                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(uiText("アプリのフォント"), fontWeight = FontWeight.SemiBold)
                        Text(
                            if (font.family == null) uiText("標準フォント") else font.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            uiText("TTF・OTF・TTC形式のフォントを取り込めます。設定画面とエディターに反映します"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                shape = UNIT_CONTROL_SHAPE,
                                border = BorderStroke(1.dp, colors.outlineVariant),
                                onClick = actions.onImportFont,
                                enabled = !font.busy,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1.35f)
                            ) {
                                AutoFitSingleLineText(
                                    text = uiText(if (font.busy) "読み込み中" else "フォントをインポート"),
                                    minFontSize = 10.5.sp
                                )
                            }
                            TextButton(
                                onClick = { actions.onResetFont() },
                                enabled = font.family != null && !font.busy,
                                shape = UNIT_CONTROL_SHAPE,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                AutoFitSingleLineText(
                                    text = uiText("標準に戻す"),
                                    minFontSize = 10.5.sp
                                )
                            }
                        }
                        Text(
                            "EDIT:RiN  Aa 0123  日本語 中文\n->  =>  !=  ===  <=  >=",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = codeFontFamily,
                                fontFeatureSettings = fontFeatures,
                                letterSpacing = 0.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceContainerLow, UNIT_CONTROL_SHAPE)
                                .border(
                                    UNIT_BORDER_WIDTH,
                                    colors.outlineVariant.copy(alpha = 0.6f),
                                    UNIT_CONTROL_SHAPE
                                )
                                .padding(12.dp)
                        )
                    }
                    SettingSwitchRow(
                        title = uiText("フォントの連字"),
                        description = uiText("対応フォントでは、複数の文字をつなげて表示する連字を使います。コードの文字列は変わりません"),
                        checked = state.fontLigatures,
                        onCheckedChange = { onSettingsChange { copy(fontLigatures = it) } }
                    )
                    SettingsDivider()

                    SettingSwitchRow(
                        title = uiText("ステータスバーを非表示"),
                        description = uiText("時刻と通知アイコンの表示を切り替えます") + "\n" + uiText("横画面では両方のバーが常に非表示になります"),
                        checked = !state.showStatusBar,
                        onCheckedChange = { hide -> onSettingsChange { copy(showStatusBar = !hide) } }
                    )
                    SettingSwitchRow(
                        title = uiText("ナビゲーションバーを非表示"),
                        description = uiText("OSの戻る・ホーム・アプリ切替バーの表示を切り替えます") + "\n" + uiText("横画面では両方のバーが常に非表示になります"),
                        checked = !state.showNavigationBar,
                        onCheckedChange = { hide -> onSettingsChange { copy(showNavigationBar = !hide) } }
                    )

                    SettingsDivider()

                    SettingSwitchRow(
                        title = uiText("横画面でノッチ部分まで使用"),
                        description = if (state.landscapeUseCutout)
                            uiText("余白なしで表示します。ノッチの位置は内容が隠れる場合があります")
                        else uiText("ノッチを避ける余白を確保します"),
                        checked = state.landscapeUseCutout,
                        onCheckedChange = { value -> onSettingsChange { copy(landscapeUseCutout = value) } }
                    )

                    SettingsDivider()

                    SettingSwitchRow(
                        title = uiText("画面の向きを固定"),
                        description = if (state.manualRotation) {
                            uiText("自動回転を停止し、上部の↻ボタンで縦横を切り替えます")
                        } else {
                            uiText("端末の向きに合わせて画面を回転します")
                        },
                        checked = state.manualRotation,
                        onCheckedChange = { value -> onSettingsChange { copy(manualRotation = value) } }
                    )
                }
            }

            if (tab == 1) {
                SettingsSection(
                    title = uiText("画面と実行"),
                    description = uiText("プレビューと画面の配置")
                ) {
                    SettingSwitchRow(
                        title = uiText("作品切替時に自動実行"),
                        description = uiText("作品を選択するとプレビューを更新します"),
                        checked = state.autoRun,
                        onCheckedChange = { value -> onSettingsChange { copy(autoRun = value) } }
                    )

                    SettingsDivider()
                    SettingSwitchRow(
                        title = uiText("編集時のPiP小窓プレビュー"),
                        description = uiText("縦画面での編集時（上部プレビューを隠している間）にフローティング小窓を表示します"),
                        checked = state.compactPreview,
                        onCheckedChange = { value ->
                            onSettingsChange { copy(hideEditingPreview = true, compactPreview = value) }
                        }
                    )

                    SettingsDivider()

                    SettingSwitchRow(
                        title = uiText("サイズ調整バー"),
                        description = if (state.showResizeHandles) {
                            uiText("プレビューとエディターの間に、ドラッグで動かせる仕切りを表示します")
                        } else {
                            uiText("仕切りを隠し、ドラッグ操作を無効にします")
                        },
                        checked = state.showResizeHandles,
                        onCheckedChange = { value -> onSettingsChange { copy(showResizeHandles = value) } }
                    )

                    SettingsDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = uiText("横画面のプレビュー幅"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = uiText("プレビューに使う横幅を調整します"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LANDSCAPE_PREVIEW_SPLITS.forEach { fraction ->
                                ThemeChip(
                                    modifier = Modifier.weight(1f),
                                    label = "${kotlin.math.round(fraction * 100).toInt()}%",
                                    selected = kotlin.math.abs(state.landscapePreviewFraction - fraction) < 0.01f,
                                    onClick = {
                                        onSettingsChange { copy(landscapePreviewFraction = normalizedLandscapeSplit(fraction)) }
                                    }
                                )
                            }
                        }
                    }

                    SettingsDivider()

                    SettingSwitchRow(
                        title = uiText("横画面の配置（エディターを左）"),
                        description = if (state.landscapeEditorOnLeft)
                            uiText("左にエディター、右にプレビューを表示します")
                        else uiText("左にプレビュー、右にエディターを表示します（従来）"),
                        checked = state.landscapeEditorOnLeft,
                        onCheckedChange = { value -> onSettingsChange { copy(landscapeEditorOnLeft = value) } }
                    )

                    SettingsDivider()

                    SettingSwitchRow(
                        title = uiText("全画面でも描画サイズを維持"),
                        description = uiText("通常プレビューと同じ座標・縦横比で実行し、表示だけを拡大します"),
                        checked = state.preserveExpandedPreview,
                        onCheckedChange = { value -> onSettingsChange { copy(preserveExpandedPreview = value) } }
                    )
                }
            }

            if (tab == 2) {
                SettingsSection(
                    title = uiText("エディター"),
                    description = uiText("文字入力と編集キー")
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiText("文字サイズ"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = uiText("コードエディターの文字サイズ"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.secondaryContainer
                            ) {
                                Text(
                                    text = "${state.editorFontSize.toInt()} sp",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.onSecondaryContainer,
                                    fontFamily = codeFontFamily
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        StudioSlider(
                            value = state.editorFontSize,
                            onValueChange = { value -> onSettingsChange { copy(editorFontSize = value) } },
                            valueRange = 12f..28f,
                            steps = 15
                        )
                    }
                    SettingsDivider()
                    SettingSwitchRow(
                        title = uiText("自動インデント"),
                        description = uiText("改行時に現在の字下げを引き継ぎ、括弧内を一段下げます"),
                        checked = state.autoIndent,
                        onCheckedChange = { value -> onSettingsChange { copy(autoIndent = value) } }
                    )

                    SettingsDivider()

                    SettingSwitchRow(
                        title = uiText("行番号"),
                        description = uiText("コードの各行に番号を表示します"),
                        checked = state.showLineNumbers,
                        onCheckedChange = { value -> onSettingsChange { copy(showLineNumbers = value) } }
                    )

                    SettingsDivider()

                    SettingSwitchRow(
                        title = uiText("コードを画面幅で折り返す"),
                        description = if (state.editorWordWrap)
                            uiText("長い行を画面幅に合わせて折り返します")
                        else uiText("長い行は折り返さず、横スクロールで表示します"),
                        checked = state.editorWordWrap,
                        onCheckedChange = { value -> onSettingsChange { copy(editorWordWrap = value) } }
                    )
                    SettingsDivider()
                    SettingSwitchRow(
                        title = uiText("入力候補"),
                        description = uiText("p5.jsの関数名や変数名の候補を表示します"),
                        checked = state.codeCompletion,
                        onCheckedChange = { value -> onSettingsChange { copy(codeCompletion = value) } }
                    )
                    SettingsDivider()
                    SettingSwitchRow(
                        title = uiText("インライン変数ウォッチ"),
                        description = uiText("// ? の行の値をエディター上にリアルタイム表示します"),
                        checked = state.inlineWatchEnabled,
                        onCheckedChange = { value -> onSettingsChange { copy(inlineWatchEnabled = value) } }
                    )
                    SettingsDivider()
                    SettingSwitchRow(
                        title = uiText("編集キー"),
                        description = uiText("編集中にTAB、カーソル移動、記号ボタンを表示します"),
                        checked = state.showEditorAccessoryBar,
                        onCheckedChange = { value -> onSettingsChange { copy(showEditorAccessoryBar = value) } }
                    )
                    SettingsDivider()
                    SettingSwitchRow(
                        title = uiText("移動キーを表示"),
                        description = uiText("操作パネルにTABとカーソルキーを表示します"),
                        checked = state.showAccessoryNavigation,
                        enabled = state.showEditorAccessoryBar,
                        onCheckedChange = { value -> onSettingsChange { copy(showAccessoryNavigation = value) } }
                    )

                    SettingsDivider()

                    SettingSwitchRow(
                        title = uiText("記号キーを表示"),
                        description = uiText("操作パネルに括弧や記号キーを表示します"),
                        checked = state.showAccessorySymbols,
                        enabled = state.showEditorAccessoryBar,
                        onCheckedChange = { value -> onSettingsChange { copy(showAccessorySymbols = value) } }
                    )

                    SettingsDivider()

                    SettingSwitchRow(
                        title = uiText("編集キーを小さくする"),
                        description = uiText("操作パネルの高さとキー幅を小さくします"),
                        checked = state.compactAccessoryKeys,
                        enabled = state.showEditorAccessoryBar,
                        onCheckedChange = { value -> onSettingsChange { copy(compactAccessoryKeys = value) } }
                    )
                }
            }

            if (tab == 3) {
                SettingsSection(
                    title = uiText("保存"),
                    description = uiText("保存の動作と保存先")
                ) {
                    SettingSwitchRow(
                        title = uiText("未保存のコードを復元"),
                        description = uiText("未保存の変更を一時保存し、次回起動時に復元します"),
                        checked = state.draftRecovery,
                        onCheckedChange = { value -> onSettingsChange { copy(draftRecovery = value) } }
                    )

                    SettingsDivider()

                    SettingSwitchRow(
                        title = uiText("アプリを離れるときに自動保存"),
                        description = uiText("別のアプリへ切り替えたとき、編集中の作品を自動で保存します"),
                        checked = state.autoSaveOnLeave,
                        onCheckedChange = { value -> onSettingsChange { copy(autoSaveOnLeave = value) } }
                    )

                    SettingsDivider()
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp)
                    ) {
                        Text(
                            text = uiText("作品フォルダー"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(6.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = UNIT_CONTROL_SHAPE,
                            color = colors.surfaceContainerLow,
                            border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = folderName,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                fontFamily = codeFontFamily
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        FilledTonalButton(
                            shape = UNIT_CONTROL_SHAPE,
                            colors = unitTonalButtonColors(colors),
                            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = actions.onChooseFolder
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_folder_code),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(uiText("保存先を変更"))
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            shape = UNIT_CONTROL_SHAPE,
                            border = BorderStroke(1.dp, colors.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = actions.onImportOfficialSamples
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_snippet),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(uiText("サンプルフォルダーを開く"))
                        }
                    }
                }

                SettingsSection(
                    title = uiText("バックアップ"),
                    description = uiText("ZIPで保存・復元")
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(
                            text = uiText("作品と設定のバックアップ"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = uiText("すべての作品とエディター設定をZIPで保存・復元します"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                shape = UNIT_CONTROL_SHAPE,
                                border = BorderStroke(1.dp, colors.outlineVariant),
                                onClick = actions.onImportBackup,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_restore),
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                AutoFitSingleLineText(uiText("復元"), minFontSize = 10.5.sp)
                            }
                            FilledTonalButton(
                                shape = UNIT_CONTROL_SHAPE,
                                colors = unitTonalButtonColors(colors),
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f)),
                                onClick = actions.onExportBackup,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_save),
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                AutoFitSingleLineText(uiText("ZIP保存"), minFontSize = 10.5.sp)
                            }
                        }
                    }
                }

                SettingsSection(
                    title = uiText("ファイルと外部連携"),
                    description = uiText("JS入出力・HTML書き出し・p5.js Web Editor")
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(
                            text = uiText("JavaScriptファイル"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = uiText(".js ファイルを作品として読み込んだり、現在のコードを書き出したりできます"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                shape = UNIT_CONTROL_SHAPE,
                                border = BorderStroke(1.dp, colors.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f),
                                onClick = actions.onImportJs
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_import_js),
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                AutoFitSingleLineText(uiText("JSをインポート"), minFontSize = 10.5.sp)
                            }

                            FilledTonalButton(
                                shape = UNIT_CONTROL_SHAPE,
                                colors = unitTonalButtonColors(colors),
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f),
                                onClick = actions.onExportJs
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_export_js),
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                AutoFitSingleLineText(uiText("JSを書き出す"), minFontSize = 10.5.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            shape = UNIT_CONTROL_SHAPE,
                            border = BorderStroke(1.dp, colors.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = actions.onExportHtml
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_export_js),
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(uiText("HTMLを書き出す"))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = uiText("単体のHTMLファイルとして作品を書き出します"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    SettingsDivider()
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(
                            text = uiText("p5.js Web Editor"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = uiText("アカウントの公開作品を選んで取り込みます"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        if (state.p5Username.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "@${state.p5Username}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.primary
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            shape = UNIT_CONTROL_SHAPE,
                            colors = unitTonalButtonColors(colors),
                            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = actions.onImportP5
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_import_js),
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(uiText("p5.jsから作品を取り込む"))
                        }
                    }
                }
            }

            if (tab == 4) {
                SettingsSection(
                    title = uiText("録画と共有"),
                    description = uiText("録画の画質と共有時の定型文")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = uiText("MP4ビットレート"),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = uiText("値を高くすると画質が上がり、ファイルサイズも大きくなります"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MP4_BITRATE_OPTIONS.forEach { bitrate ->
                                ThemeChip(
                                    modifier = Modifier.widthIn(min = 88.dp),
                                    label = "$bitrate Mbps",
                                    selected = state.mp4BitrateMbps == bitrate,
                                    onClick = { onSettingsChange { copy(mp4BitrateMbps = bitrate) } }
                                )
                            }
                        }

                        Text(
                            text = uiText("録画開始カウントダウン"),
                            style = MaterialTheme.typography.labelLarge
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RECORDING_COUNTDOWN_OPTIONS.forEach { seconds ->
                                ThemeChip(
                                    modifier = Modifier.widthIn(min = 88.dp),
                                    label = if (seconds == 0) uiText("なし") else uiText("%s秒", seconds),
                                    selected = state.recordingCountdownSeconds == seconds,
                                    onClick = { onSettingsChange { copy(recordingCountdownSeconds = seconds) } }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = state.xShareText,
                            onValueChange = onXShareTextChange,
                            modifier = Modifier.fillMaxWidth(),
                            shape = UNIT_CONTROL_SHAPE,
                            label = { Text(uiText("X共有の定型文")) },
                            supportingText = {
                                Text(uiText("Xで共有するとき、録画と一緒に定型文を入力します"))
                            },
                            minLines = 3,
                            maxLines = 6
                        )
                        TextButton(
                            onClick = { onXShareTextChange(DEFAULT_X_SHARE_TEXT) },
                            shape = UNIT_CONTROL_SHAPE,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(uiText("初期値に戻す"))
                        }
                    }
                }

                SettingsSection(
                    title = uiText("シェアカード初期設定"),
                    description = uiText("シェアカード設定")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = state.shareCardAuthor,
                            onValueChange = { value ->
                                onSettingsChange { copy(shareCardAuthor = value.take(80)) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = UNIT_CONTROL_SHAPE,
                            singleLine = true,
                            label = { Text(uiText("デフォルトの著者名")) }
                        )
                        OutlinedTextField(
                            value = state.shareCardQrUrl,
                            onValueChange = { value ->
                                onSettingsChange { copy(shareCardQrUrl = value.take(256)) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = UNIT_CONTROL_SHAPE,
                            singleLine = true,
                            label = { Text(uiText("QRコードのリンク先URL")) }
                        )
                        TextButton(
                            onClick = { onSettingsChange { copy(shareCardQrUrl = DEFAULT_SHARE_URL) } },
                            shape = UNIT_CONTROL_SHAPE,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(uiText("初期値に戻す"))
                        }
                    }
                }
            }

            if (tab == 5) {
                SettingsAboutSection(
                    updateViewModel = updateViewModel,
                    onShowGuide = { showUserGuide = true },
                    onShowOnboarding = actions.onShowOnboarding,
                    onShowPracticeTour = actions.onShowPracticeTour,
                    onShowLicenses = { showLicenses = true },
                    onShowReleaseNotes = { showReleaseNotes = true },
                    openExternalUrl = actions.openExternalUrl,
                    textTranslator = textTranslator
                )
            }

            if (tab == 6 && BuildConfig.DEBUG) {
                SettingsSection(
                    title = uiText("デバッグ"),
                    description = uiText("デバッグAPK専用の検証項目")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = uiText("現在はデバッグ専用カテゴリです"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = uiText("今後のUIや試験機能の検証項目はここに配置されます（リリースビルドでは非表示）"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
