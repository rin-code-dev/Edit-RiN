package com.hikariatelier.app

import android.content.res.AssetManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.ConfigurationCompat
import com.hikariatelier.app.ui.theme.AppThemeMode

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

        val colors =
            MaterialTheme.colorScheme

        // A sideways phone or a large font does not necessarily have room for a sidebar.
        val settingsConfiguration = LocalConfiguration.current
        val settingsLandscape = settingsConfiguration.screenWidthDp >= 640 &&
            settingsConfiguration.screenWidthDp / settingsConfiguration.fontScale >= 560

        var settingsTab by rememberSaveable { mutableStateOf(0) }
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
                    onClose = { showUserGuide = false }
                )
            }

        }
        if (showLicenses) {
            val paragraphs = remember {
                assets.open("licenses/THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
                    .split("\n\n")
            }
            EditSettingsDialog(
                onDismissRequest = { showLicenses = false },
                title = { Text(uiText("ライセンス情報")) },
                text = {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(paragraphs) { paragraph ->
                            Text(paragraph, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLicenses = false }) { Text(uiText("閉じる")) }
                }
            )
        }
        val categoryScrolls = List(4) { rememberScrollState() }
        val settingsScroll = categoryScrolls[settingsTab]

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
                    modifier = Modifier.width(184.dp).fillMaxHeight(),
                    color = colors.surface,
                    contentColor = colors.onSurface,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = actions.onBack) {
                                Icon(painterResource(R.drawable.ic_back), uiText("戻る"), Modifier.size(20.dp))
                            }
                            IconButton(onClick = { showUserGuide = true }) {
                                Icon(painterResource(R.drawable.ic_help), uiText("使い方ガイド"), Modifier.size(20.dp), tint = colors.onSurface)
                            }
                        }
                        Text(uiText("設定"), Modifier.padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        listOf(
                            R.drawable.ic_settings to uiText("外観"),
                            R.drawable.ic_code to uiText("エディター"),
                            R.drawable.ic_folder_code to uiText("保存とバックアップ"),
                            R.drawable.ic_more_horizontal to "About"
                        ).forEachIndexed { index, (icon, label) ->
                            Surface(
                                onClick = { settingsTab = index },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = if (settingsTab == index) colors.primary.copy(alpha = 0.1f)
                                    else colors.surface,
                                contentColor = colors.onSurface,
                                border = if (settingsTab == index) BorderStroke(1.dp, colors.primary)
                                    else null
                            ) {
                                Row(Modifier.heightIn(min = 48.dp).padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painterResource(icon), null, Modifier.size(18.dp),
                                        tint = if (settingsTab == index) colors.primary else colors.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text(label, modifier = Modifier.weight(1f),
                                        color = colors.onSurface,
                                        style = MaterialTheme.typography.labelLarge)
                                }
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

            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp
                )
        ) {

            if (!settingsLandscape) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                IconButton(
                    onClick =
                        actions.onBack
                ) {

                    Icon(
                        painter =
                            painterResource(
                                R.drawable.ic_back
                            ),
                        contentDescription =
                            uiText("戻る"),
                        tint =
                            colors.onSurface,
                        modifier =
                            Modifier.size(22.dp)
                    )
                }

                Spacer(
                    Modifier.width(
                        6.dp
                    )
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = uiText("設定"),
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Text(
                        text = "Edit:RiN",
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,
                        color =
                            colors.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { showUserGuide = true }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_help),
                        contentDescription = uiText("使い方ガイド"),
                        tint = colors.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            }

            if (!settingsLandscape) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        R.drawable.ic_settings to uiText("外観"),
                        R.drawable.ic_code to uiText("エディター"),
                        R.drawable.ic_folder_code to uiText("保存とバックアップ"),
                        R.drawable.ic_more_horizontal to "About"
                    ).forEachIndexed { index, (icon, label) ->
                        FilterChip(
                            selected = settingsTab == index,
                            onClick = { settingsTab = index },
                            label = { Text(label) },
                            leadingIcon = { Icon(painterResource(icon), null, Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }

            if (settingsTab == 0) {
            SettingsSection(
                title = uiText("外観"),
                description = uiText("テーマとシステムUI")
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiText("言語"), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text(uiText("対応していない端末言語の場合は英語を使用します"),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    listOf("system" to uiText("システムデフォルト"), "ja" to "日本語",
                        "en" to "English", "zh" to "中文（简体）").chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (code, label) ->
                                FilterChip(
                                    selected = state.appLanguage == code,
                                    onClick = {
                                        onSettingsChange { copy(appLanguage = code) }
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                SettingsDivider()

                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp)
                ) {
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
                    FilledTonalButton(shape = ButtonDefaults.filledTonalShape,
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

                SettingsDivider()

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 18.dp,
                                vertical = 16.dp
                            )
                ) {

                    Text(
                        text = uiText("テーマ"),
                        style =
                            MaterialTheme
                                .typography
                                .titleSmall,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Spacer(
                        Modifier.height(
                            4.dp
                        )
                    )

                    Text(
                        text =
                            when (state.themeMode) {
                                AppThemeMode.SYSTEM ->
                                    uiText("端末の設定とMaterial Youに合わせます")

                                AppThemeMode.DARK ->
                                    uiText("黒を基調とした表示")

                                AppThemeMode.LIGHT ->
                                    uiText("白を基調とした表示")

                                AppThemeMode.CUSTOM ->
                                    uiText("背景とアクセントカラーを設定")
                            },
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            colors.onSurfaceVariant
                    )

                    Spacer(
                        Modifier.height(
                            14.dp
                        )
                    )

                    FlowRow(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            ),
                        maxItemsInEachRow = 2
                    ) {

                        ThemeChip(
                            modifier =
                                Modifier.weight(1f),
                            label = uiText("自動"),
                            selected =
                                state.themeMode ==
                                    AppThemeMode.SYSTEM,
                            onClick = {
                                onThemeModeChange(
                                    AppThemeMode.SYSTEM
                                )
                            }
                        )

                        ThemeChip(
                            modifier =
                                Modifier.weight(1f),
                            label = uiText("ダーク"),
                            selected =
                                state.themeMode ==
                                    AppThemeMode.DARK,
                            onClick = {
                                onThemeModeChange(
                                    AppThemeMode.DARK
                                )
                            }
                        )

                        ThemeChip(
                            modifier =
                                Modifier.weight(1f),
                            label = uiText("ライト"),
                            selected =
                                state.themeMode ==
                                    AppThemeMode.LIGHT,
                            onClick = {
                                onThemeModeChange(
                                    AppThemeMode.LIGHT
                                )
                            }
                        )

                        ThemeChip(
                            modifier =
                                Modifier.weight(1f),
                            label = uiText("カスタム"),
                            selected =
                                state.themeMode ==
                                    AppThemeMode.CUSTOM,
                            onClick = {
                                onThemeModeChange(
                                    AppThemeMode.CUSTOM
                                )
                            }
                        )
                    }
                }

                SettingsDivider()

                if (state.themeMode == AppThemeMode.CUSTOM) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CustomColorSetting(uiText("背景色"), state.customBackground, { onSettingsChange { copy(customBackground = it) } }, { s -> uiText(s) })
                        CustomColorSetting(uiText("アクセントカラー"), state.customAccent, { onSettingsChange { copy(customAccent = it) } }, { s -> uiText(s) })
                    }
                    SettingsDivider()
                }

                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(uiText("アプリのフォント"), fontWeight = FontWeight.SemiBold)
                    Text(if (font.family == null) uiText("標準フォント") else font.name,
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(uiText("TTF・OTF・TTCをインポート。設定画面とエディターに反映します"),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(shape = ButtonDefaults.outlinedShape, onClick = actions.onImportFont, enabled = !font.busy,
                            modifier = Modifier.weight(1f)) {
                            Text(uiText(if (font.busy) "読み込み中" else "フォントをインポート"))
                        }
                        TextButton(onClick = {
                            actions.onResetFont()
                        }, enabled = font.family != null && !font.busy,
                            modifier = Modifier.weight(1f)) { Text(uiText("標準に戻す")) }
                    }
                    Text("EDIT:RiN  Aa 0123  日本語 中文\n->  =>  !=  ===  <=  >=",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = codeFontFamily, fontFeatureSettings = fontFeatures,
                            letterSpacing = 0.sp),
                        modifier = Modifier.fillMaxWidth().background(colors.surface,
                            RoundedCornerShape(12.dp)).padding(12.dp))
                }
                SettingSwitchRow(
                    title = uiText("フォントの連字"),
                    description = uiText("対応フォントの連字を有効にします。コードの文字列は変わりません"),
                    checked = state.fontLigatures,
                    onCheckedChange = {
                        onSettingsChange { copy(fontLigatures = it) }
                    }
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
                    title =
                        uiText("画面の向きを固定"),
                    description =
                        if (state.manualRotation) {
                            uiText("自動回転を停止し、上部の↻ボタンで縦横を切り替えます")
                        } else {
                            uiText("端末の向きに合わせて画面を回転します")
                        },
                    checked =
                        state.manualRotation,
                    onCheckedChange =
                        { value -> onSettingsChange { copy(manualRotation = value) } }
                )
            }

            }

            if (settingsTab == 1) {
            SettingsSection(
                title = uiText("エディター"),
                description = uiText("編集とプレビューの動作")
            ) {

                SettingSwitchRow(
                    title =
                        uiText("作品切替時に自動実行"),
                    description =
                        uiText("作品を選択するとプレビューを更新します"),
                    checked =
                        state.autoRun,
                    onCheckedChange =
                        { value -> onSettingsChange { copy(autoRun = value) } }
                )

                SettingsDivider()

                SettingSwitchRow(
                    title = uiText("編集時にプレビューを隠す"),
                    description = uiText("縦画面でコード欄を選ぶと非表示になり、編集を終えると戻ります"),
                    checked = state.hideEditingPreview,
                    onCheckedChange = { value -> onSettingsChange { copy(hideEditingPreview = value) } }
                )

                SettingsDivider()

                SettingSwitchRow(
                    enabled = !state.hideEditingPreview,
                    title =
                        uiText("編集時にプレビューを縮小"),
                    description =
                        if (state.hideEditingPreview) uiText("プレビューを隠す設定が優先されます")
                        else uiText("縦画面の編集中だけプレビューを低く表示します"),
                    checked =
                        state.compactPreview,
                    onCheckedChange =
                        { value -> onSettingsChange { copy(compactPreview = value) } }
                )

                SettingsDivider()

                SettingSwitchRow(
                    title = uiText("サイズ調整バー"),
                    description = if (state.showResizeHandles) {
                        uiText("プレビューとエディターの間にドラッグ操作を表示します")
                    } else {
                        uiText("境界線とドラッグ操作を非表示にします")
                    },
                    checked = state.showResizeHandles,
                    onCheckedChange = { value -> onSettingsChange { copy(showResizeHandles = value) } }
                )

                if (settingsLandscape) {
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
                                    selected = kotlin.math.abs(
                                        state.landscapePreviewFraction - fraction
                                    ) < 0.01f,
                                    onClick = {
                                        onSettingsChange { copy(landscapePreviewFraction = normalizedLandscapeSplit(fraction)) }
                                    }
                                )
                            }
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
                        uiText("長い行を画面幅に合わせて下段に折り返します")
                    else uiText("折り返さずに1行で表示し、横スクロールできるようにします"),
                    checked = state.editorWordWrap,
                    onCheckedChange = { value -> onSettingsChange { copy(editorWordWrap = value) } }
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
                    title = uiText("入力候補"),
                    description = uiText("p5.jsの関数名や変数名の候補を表示します"),
                    checked = state.codeCompletion,
                    onCheckedChange = { value -> onSettingsChange { copy(codeCompletion = value) } }
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

                SettingsDivider()

                SettingSwitchRow(
                    title =
                        uiText("未保存のコードを復元"),
                    description =
                        uiText("未保存の変更を一時保存し、次回起動時に復元します"),
                    checked =
                        state.draftRecovery,
                    onCheckedChange =
                        { value -> onSettingsChange { copy(draftRecovery = value) } }
                )

                SettingsDivider()

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 18.dp,
                                vertical = 16.dp
                            )
                ) {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                text = uiText("文字サイズ"),
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleSmall,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Text(
                                text =
                                    uiText("コードエディターの文字サイズ"),
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,
                                color =
                                    colors.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape =
                                RoundedCornerShape(
                                    10.dp
                                ),
                            color =
                                colors.secondaryContainer
                        ) {

                            Text(
                                text =
                                    "${state.editorFontSize.toInt()} sp",
                                modifier =
                                    Modifier.padding(
                                        horizontal = 10.dp,
                                        vertical = 6.dp
                                    ),
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelLarge,
                                color =
                                    colors.onSecondaryContainer,
                                fontFamily =
                                    codeFontFamily
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    Slider(
                        value =
                            state.editorFontSize,
                        onValueChange =
                            { value -> onSettingsChange { copy(editorFontSize = value) } },
                        valueRange =
                            12f..20f,
                        steps =
                            7
                    )
                }
            }

            }

            if (settingsTab == 2) {
            SettingsSection(
                title = uiText("保存とバックアップ"),
                description = uiText("保存先とファイル入出力")
            ) {

                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = uiText("録画と共有"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = uiText("MP4ビットレート"),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = uiText("高い値ほど画質とファイルサイズが大きくなります"),
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
                        label = { Text(uiText("X共有の定型文")) },
                        supportingText = {
                            Text(uiText("Xで共有するときに録画と一緒に入力します"))
                        },
                        minLines = 3,
                        maxLines = 6
                    )
                    TextButton(
                        onClick = { onXShareTextChange(DEFAULT_X_SHARE_TEXT) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(uiText("初期値に戻す"))
                    }
                }

                SettingsDivider()

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                18.dp
                            )
                ) {

                    Text(
                        text = uiText("作品フォルダー"),
                        style =
                            MaterialTheme
                                .typography
                                .titleSmall,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Spacer(
                        Modifier.height(
                            6.dp
                        )
                    )

                    Surface(
                        modifier =
                            Modifier.fillMaxWidth(),
                        shape =
                            RoundedCornerShape(
                                12.dp
                            ),
                        color =
                            colors.surface
                    ) {

                        Text(
                            text =
                                folderName,
                            modifier =
                                Modifier.padding(
                                    horizontal = 14.dp,
                                    vertical = 12.dp
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                            color =
                                colors.onSurfaceVariant,
                            fontFamily =
                                codeFontFamily
                        )
                    }

                    Spacer(
                        Modifier.height(
                            12.dp
                        )
                    )

                    FilledTonalButton(shape = ButtonDefaults.filledTonalShape,
                        modifier =
                            Modifier.fillMaxWidth(),
                        onClick =
                            actions.onChooseFolder
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    R.drawable.ic_folder_code
                                ),
                            contentDescription =
                                null,
                            modifier =
                                Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            uiText("保存先を変更")
                        )
                    }

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    OutlinedButton(shape = ButtonDefaults.outlinedShape,
                        modifier =
                            Modifier.fillMaxWidth(),
                        onClick =
                            actions.onImportOfficialSamples
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    R.drawable.ic_snippet
                                ),
                            contentDescription =
                                null,
                            modifier =
                                Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            uiText("公式サンプル作品を追加")
                        )
                    }
                }

                SettingsDivider()

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                18.dp
                            )
                ) {

                    Text(
                        text =
                            uiText("JavaScriptファイル"),
                        style =
                            MaterialTheme
                                .typography
                                .titleSmall,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Spacer(
                        Modifier.height(
                            4.dp
                        )
                    )

                    Text(
                        text =
                            uiText(".js を作品として読み込む、または現在のコードを書き出します"),
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            colors.onSurfaceVariant
                    )

                    Spacer(
                        Modifier.height(
                            12.dp
                        )
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {

                        OutlinedButton(shape = ButtonDefaults.outlinedShape,
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            onClick =
                                actions.onImportJs
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable.ic_import_js
                                    ),
                                contentDescription =
                                    null,
                                modifier =
                                    Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Import .js"
                            )
                        }

                        FilledTonalButton(shape = ButtonDefaults.filledTonalShape,
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            onClick =
                                actions.onExportJs
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable.ic_export_js
                                    ),
                                contentDescription =
                                    null,
                                modifier =
                                    Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Export .js"
                            )
                        }
                    }
                }

                SettingsDivider()

                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp)
                ) {
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
                        OutlinedButton(shape = ButtonDefaults.outlinedShape,
                            onClick = actions.onImportBackup,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_restore),
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(uiText("復元"))
                        }
                        FilledTonalButton(shape = ButtonDefaults.filledTonalShape,
                            onClick = actions.onExportBackup,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_save),
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(uiText("ZIP保存"))
                        }
                    }
                }
            }

            }

            if (settingsTab == 3) {
                SettingsAboutSection(updateViewModel,
                    onShowGuide = { showUserGuide = true },
                    onShowLicenses = { showLicenses = true },
                    onShowReleaseNotes = { showReleaseNotes = true },
                    openExternalUrl = actions.openExternalUrl, textTranslator = textTranslator)

            }
        }
    }
}
