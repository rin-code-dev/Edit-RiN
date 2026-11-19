package com.hikariatelier.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun guideText(language: String, ja: String, en: String, zh: String): String =
    when (language) { "ja" -> ja; "zh" -> zh; else -> en }

internal data class GuideAction(val icon: Int, val label: String, val result: String)

internal fun guideControlActions(language: String): List<GuideAction> {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    return listOf(
        GuideAction(R.drawable.ic_pause, t("一時停止", "Pause", "暂停"),
            t("作品の動きを止めます。もう一度押すと再開します。", "Pause the artwork; press again to resume.", "暂停作品；再次点击即可继续。")),
        GuideAction(R.drawable.ic_reload, t("変更を実行", "Run changes", "运行更改"),
            t("編集したコードを実行してプレビューへ反映します。", "Run edited code to update the preview.", "运行编辑后的代码并更新预览。")),
        GuideAction(R.drawable.ic_log, t("ログ", "Logs", "日志"),
            t("実行中のメッセージやエラーを確認します。", "Inspect runtime messages and errors.", "查看运行消息和错误。")),
        GuideAction(R.drawable.ic_tune, t("パラメータ", "Parameters", "参数"),
            t("コードに宣言された数値・色などを調整します。", "Adjust numbers and colors declared in the code.", "调整代码中声明的数值和颜色。")),
        GuideAction(R.drawable.ic_camera, t("プレビュー操作", "Preview actions", "预览操作"),
            t("スクリーンショットや録画などの操作を開きます。", "Open screenshot and recording actions.", "打开截图和录制操作。")),
        GuideAction(R.drawable.ic_history, t("履歴", "History", "历史"),
            t("スナップショットの作成や、保存済み状態への復元を選びます。", "Choose snapshots or revert to the saved work.", "选择快照或恢复到已保存状态。")),
        GuideAction(R.drawable.ic_save, t("保存", "Save", "保存"),
            t("作品の全ファイルを保存します。", "Save all files in the work.", "保存作品的所有文件。"))
    )
}

// Explicit chapter identity; captions and assets are shared across all translations.
internal fun guideHighlightedControls(sectionIcon: Int): Set<Int> = when (sectionIcon) {
    R.drawable.ic_code -> setOf(R.drawable.ic_pause, R.drawable.ic_reload, R.drawable.ic_log)
    R.drawable.ic_tune -> setOf(R.drawable.ic_tune)
    R.drawable.ic_history -> setOf(R.drawable.ic_history, R.drawable.ic_save)
    R.drawable.ic_camera -> setOf(R.drawable.ic_camera)
    else -> emptySet()
}

/** Playground tab body: interactive controls, live comment cheat sheet, and block lab. */
@Composable
internal fun GuideQuickStart(
    language: String,
    onTryCodeInEditor: ((title: String, code: String) -> Unit)? = null
) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    Column(verticalArrangement = Arrangement.spacedBy(if (compactHeight) 8.dp else 14.dp)) {
        Text(
            t("操作の練習", "Practice controls", "操作练习"),
            style = if (compactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            t(
                "ここは練習専用の画面です。自分の作品や端末ファイルは変更されません。下のパネルでプレビュー操作やコメント記法、ブロック編集を試せます。",
                "Practice with the controls, comment directives, and blocks below. Nothing here changes your saved works or files.",
                "这里是练习专用画面，不会修改您的作品或文件。可在下方试用预览操作、注释语法与积木编辑。"
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        GuideControlIllustration(R.drawable.ic_play, language)
        GuideCommentCheatSheet(language, onTryCodeInEditor)
        GuideBlockModeIllustration(language)
    }
}

/** Chapter-specific interactive illustration card router. */
@Composable
internal fun GuideChapterVisual(sectionIndex: Int, iconRes: Int, language: String) {
    when (sectionIndex) {
        0 -> GuideSampleWorkflowIllustration(language)
        1 -> GuideTemplateRatioIllustration(language)
        2 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GuideEditorFeaturesIllustration(language)
            GuideControlIllustration(iconRes, language)
        }
        3 -> GuideControlIllustration(R.drawable.ic_tune, language)
        4 -> GuideBlockModeIllustration(language)
        5 -> GuideProjectAssetsIllustration(language)
        6 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GuideUnifiedHistoryIllustration(language)
            GuideControlIllustration(iconRes, language)
        }
        7 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GuideExportPipelineIllustration(language)
            GuideControlIllustration(iconRes, language)
        }
        8 -> GuideOrganizationBackupIllustration(language)
        else -> if (guideHighlightedControls(iconRes).isNotEmpty()) GuideControlIllustration(iconRes, language)
    }
}

/** Chapter 0 Illustration: Samples vs My Works & Copy to edit. */
@Composable
private fun GuideSampleWorkflowIllustration(language: String) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    val palettes = listOf(
        listOf(Color(0xFFD67856), Color(0xFF3F6F8F)),
        listOf(Color(0xFF558567), Color(0xFFE5A93B)),
        listOf(Color(0xFF8A5EA6), Color(0xFFD67856))
    )
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 = Samples, 1 = My Works
    var paletteIdx by rememberSaveable { mutableIntStateOf(0) }
    var copied by rememberSaveable { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text(t("サンプル", "Samples", "示例")) }
                )
                FilterChip(
                    selected = tab == 1 || copied,
                    onClick = { tab = 1 },
                    label = { Text(if (copied) t("自分の作品 (コピー済み)", "My works (1 copied)", "我的作品（已复制）") else t("自分の作品", "My works", "我的作品")) }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF2EFE7))
                    .clickable { paletteIdx = (paletteIdx + 1) % palettes.size },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = palettes[paletteIdx]
                    drawCircle(c[0], radius = size.minDimension * 0.28f, center = Offset(size.width * 0.42f, size.height * 0.5f))
                    drawCircle(c[1].copy(alpha = 0.85f), radius = size.minDimension * 0.24f, center = Offset(size.width * 0.58f, size.height * 0.5f))
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surface.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Text(
                        text = if (tab == 0) t("Palette · 閲覧専用", "Palette · Read-only", "Palette · 只读") else t("My Palette · 編集可", "My Palette · Editable", "My Palette · 可编辑"),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t("キャンバスをタップすると配色が変わります", "Tap the canvas to change the palette", "点击画布可切换配色"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = { copied = true; tab = 1 },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (copied) t("自分の作品にコピーしました", "Copied to My works", "已复制到我的作品")
                        else t("コピーして編集", "Copy to edit", "复制后编辑"),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Chapter 1 Illustration: Template selector & Canvas Aspect Ratio preview. */
@Composable
private fun GuideTemplateRatioIllustration(language: String) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    val templates = listOf("2D", "Animation", "Parameters", "WebGL")
    val ratios = listOf("1:1" to 1f, "4:3" to 4f / 3f, "16:9" to 16f / 9f, "9:16" to 9f / 16f)
    var tmplIdx by rememberSaveable { mutableIntStateOf(0) }
    var ratioIdx by rememberSaveable { mutableIntStateOf(0) }

    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("テンプレート選択", "Starting template", "选择起始模板"), style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                templates.forEachIndexed { idx, name ->
                    FilterChip(selected = tmplIdx == idx, onClick = { tmplIdx = idx }, label = { Text(name) })
                }
            }
            Text(t("プレビュー比率", "Preview ratio", "预览比例"), style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ratios.forEachIndexed { idx, (label, _) ->
                    FilterChip(selected = ratioIdx == idx, onClick = { ratioIdx = idx }, label = { Text(label) })
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(110.dp).background(colors.surfaceContainerHigh, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .height(if (ratios[ratioIdx].second < 1f) 96.dp else 82.dp)
                        .aspectRatio(ratios[ratioIdx].second)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF2EFE7))
                        .border(1.5.dp, colors.primary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${templates[tmplIdx]} · ${ratios[ratioIdx].first}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2B2B2B))
                }
            }
        }
    }
}

/** Chapter 2 Illustration: Code folding, number scrubbing, and error jump. */
@Composable
private fun GuideEditorFeaturesIllustration(language: String) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    var folded by rememberSaveable { mutableStateOf(false) }
    var radiusVal by rememberSaveable { mutableIntStateOf(120) }
    var jumpedLine by rememberSaveable { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { folded = !folded }
                    ) {
                        Text(if (folded) "▸ " else "▾ ", fontFamily = FontFamily.Monospace, color = colors.primary, fontWeight = FontWeight.Bold)
                        Text("function draw() {", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        if (folded) {
                            Spacer(Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = colors.secondaryContainer) {
                                Text(" ... ", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    if (!folded) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("  circle(200, 200, ", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            Surface(
                                onClick = { radiusVal = if (radiusVal >= 180) 60 else radiusVal + 30 },
                                shape = RoundedCornerShape(4.dp),
                                color = colors.primaryContainer
                            ) {
                                Text(" $radiusVal ", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
                            }
                            Text(");", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(
                            color = if (jumpedLine) colors.errorContainer else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { folded = !folded },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(if (folded) t("展開", "Expand", "展开") else t("折りたたみ", "Fold", "折叠"), maxLines = 1)
                }
                OutlinedButton(
                    onClick = { jumpedLine = !jumpedLine },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(t("エラー行へ移動", "Error jump", "错误跳转"), maxLines = 1)
                }
            }
        }
    }
}

/** Chapter 5 Illustration: Multi-file project tabs, assets & p5 version switch. */
@Composable
private fun GuideProjectAssetsIllustration(language: String) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    val files = listOf("sketch.js", "helper.js", "index.html")
    var fileIdx by rememberSaveable { mutableIntStateOf(0) }
    var p5V2 by rememberSaveable { mutableStateOf(true) }
    var insertedAsset by rememberSaveable { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                files.forEachIndexed { idx, name ->
                    FilterChip(selected = fileIdx == idx, onClick = { fileIdx = idx }, label = { Text(name) })
                }
            }
            Surface(shape = RoundedCornerShape(8.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = when {
                        fileIdx == 1 -> "export function pulse(t) { return sin(t); }"
                        fileIdx == 2 -> "<script src=\"sketch.js\"></script>"
                        insertedAsset && p5V2 -> "const img = await loadImage('assets/photo.png');"
                        insertedAsset -> "function preload() { img = loadImage('assets/photo.png'); }"
                        else -> "// p5.js ${if (p5V2) "2.3.4" else "1.11.5"} ready"
                    },
                    modifier = Modifier.padding(10.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = p5V2, onClick = { p5V2 = true }, label = { Text("p5 2.3.4") })
                FilterChip(selected = !p5V2, onClick = { p5V2 = false }, label = { Text("p5 1.11.5") })
            }
            FilledTonalButton(
                onClick = { fileIdx = 0; insertedAsset = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    if (insertedAsset) t("読み込みコードを挿入しました", "Loading code inserted", "已插入加载代码")
                    else t("素材読み込みコードを挿入", "Insert loading code", "插入素材加载代码"),
                    maxLines = 1
                )
            }
        }
    }
}

/** Chapter 6 Illustration: Unified History & Snapshots mock sheet. */
@Composable
private fun GuideUnifiedHistoryIllustration(language: String) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    var filterSnapshots by rememberSaveable { mutableStateOf(false) }
    var restored by rememberSaveable { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !filterSnapshots, onClick = { filterSnapshots = false }, label = { Text(t("自動保存履歴", "Auto-saves", "自动保存")) })
                FilterChip(selected = filterSnapshots, onClick = { filterSnapshots = true }, label = { Text(t("スナップショット", "Snapshots", "快照")) })
            }
            Surface(shape = RoundedCornerShape(10.dp), color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (filterSnapshots) t("配色調整前 (メモ付き)", "Before palette tweak", "配色调整前（带备注）")
                            else t("14:20 自動保存", "14:20 Auto-save", "14:20 自动保存"),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            if (restored) t("現在のコードと一致", "Matches current code", "与当前代码一致")
                            else t("差分あり · コード＋パラメータ", "Differs · Code + Parameters", "有差异 · 代码+参数"),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (restored) colors.primary else colors.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { restored = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(t("復元", "Restore", "恢复"), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Chapter 7 Illustration: High-res PNG scale & MP4/GIF capture selector. */
@Composable
private fun GuideExportPipelineIllustration(language: String) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    var scale by rememberSaveable { mutableIntStateOf(2) }
    var format by rememberSaveable { mutableStateOf("MP4") }

    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("PNG:", style = MaterialTheme.typography.labelMedium)
                listOf(1, 2, 4).forEach { s ->
                    FilterChip(selected = scale == s, onClick = { scale = s }, label = { Text("${s}x") })
                }
                Spacer(Modifier.weight(1f))
                Text("${600 * scale}×${600 * scale} px", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = colors.primary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("録画:", "Record:", "录制:"), style = MaterialTheme.typography.labelMedium)
                listOf("MP4", "GIF").forEach { fmt ->
                    FilterChip(selected = format == fmt, onClick = { format = fmt }, label = { Text(fmt) })
                }
            }
        }
    }
}

/** Chapter 8 Illustration: Folders, Tags, Multi-select & ZIP backup preview. */
@Composable
private fun GuideOrganizationBackupIllustration(language: String) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    val folders = listOf(t("すべて", "All", "全部"), t("アニメ", "Motion", "动画"), "#webgl")
    var selectedIdx by rememberSaveable { mutableIntStateOf(0) }
    var backedUp by rememberSaveable { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                folders.forEachIndexed { idx, label ->
                    FilterChip(selected = selectedIdx == idx, onClick = { selectedIdx = idx }, label = { Text(label) })
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (backedUp) t("Edit-RiN-backup.zip を作成しました", "Backup ZIP prepared", "已准备好备份 ZIP")
                    else t("作品・テンプレート・設定を1つのZIPに保存します", "Pack works, templates & settings", "打包全部作品、模板与设置"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = { backedUp = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(t("ZIPバックアップ", "Backup ZIP", "ZIP 备份"), maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun GuideCommentCheatSheet(
    language: String,
    onTryCodeInEditor: ((title: String, code: String) -> Unit)? = null
) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val entries = remember(language) {
        listOf(
            "let x = frameCount; // ?" to t(
                "行末に // ? を付けると、実行中の値がエディター上にリアルタイム表示されます",
                "Append // ? to display live runtime values right beside the code",
                "在行末添加 // ? 即可在编辑器旁实时显示运行值"
            ),
            "let size = 120; // @slider(20, 240, 10) \"Size\"" to t(
                "数値変数をスライダーで直接操作し、コードのリテラル値も同時に更新します",
                "Tune numeric variables with a slider and rewrite code literals in real time",
                "通过滑块直接调节数值变量并同步更新代码字面量"
            ),
            "let count = 6; // @int(1, 20) \"Count\"" to t(
                "整数（刻み幅 1）のスライダーを生成します",
                "Create an integer slider (step 1)",
                "创建整数滑块（步长为 1）"
            ),
            "let ink = \"#D67856\"; // @color \"Ink\"" to t(
                "#RRGGBB 色リテラルをカラーピッカーで調整します",
                "Adjust #RRGGBB color strings directly with a color picker",
                "通过取色器直接调整 #RRGGBB 颜色值"
            ),
            "let show = true; // @toggle \"Show\"" to t(
                "true / false をトグルスイッチで切り替えます",
                "Toggle a boolean variable between true and false",
                "通过开关在 true 与 false 之间切换"
            ),
            "let mode = \"circle\"; // @choice(circle, square, triangle) \"Mode\"" to t(
                "カンマ区切りの選択肢チップを作成します",
                "Create selectable option chips from a comma-separated list",
                "根据逗号分隔的列表创建选项芯片"
            ),
            "// @rin number speed \"Speed\" 0 5 1 0.1" to t(
                "作品ごとの設定パネル（rinParams.speed）を定義し、作品と一緒に保存します",
                "Declare per-work settings read via rinParams.speed and saved with the work",
                "定义保存于作品中的参数（通过 rinParams.speed 读取）"
            )
        )
    }
    val sampleTitle = remember(language) { practiceTourSampleTitle(language) }
    val sampleCode = remember(language) { practiceTourSampleCode(language) }

    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                t("コメント記法の一覧", "Comment directives", "注释语法一览"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                t(
                    "通常のJavaScriptコメントのまま動作するため、他のp5.js環境でもエラーになりません。",
                    "All tags live inside standard JS comments, so sketches remain portable everywhere.",
                    "所有标记均写在标准 JS 注释中，不影响在其他环境运行。"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            entries.forEach { (syntax, desc) ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = syntax,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("code", sampleCode))
                        Toast.makeText(context, t("コードをコピーしました", "Code copied", "已复制代码"), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(t("コードをコピー", "Copy sample", "复制示例"), maxLines = 1)
                }
                if (onTryCodeInEditor != null) {
                    FilledTonalButton(
                        onClick = { onTryCodeInEditor(sampleTitle, sampleCode) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(t("エディターで試す", "Try in Editor", "在编辑器中尝试"), maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuideControlIllustration(sectionIcon: Int, language: String) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    var diameter by rememberSaveable { mutableFloatStateOf(120f) }
    var ink by rememberSaveable { mutableIntStateOf(0) }
    var savedDiameter by rememberSaveable { mutableFloatStateOf(120f) }
    var savedInk by rememberSaveable { mutableIntStateOf(0) }
    var snapshotDiameter by rememberSaveable { mutableFloatStateOf(120f) }
    var snapshotInk by rememberSaveable { mutableIntStateOf(0) }
    var hasSnapshot by rememberSaveable { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf(if (sectionIcon == R.drawable.ic_tune) "parameters" else "") }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    var recording by rememberSaveable { mutableStateOf(false) }
    var recordingSeconds by rememberSaveable { mutableIntStateOf(0) }
    var recordedSeconds by rememberSaveable { mutableIntStateOf(-1) }
    var captured by rememberSaveable { mutableStateOf(false) }
    var captureDiameter by rememberSaveable { mutableFloatStateOf(120f) }
    var captureInk by rememberSaveable { mutableIntStateOf(0) }
    var message by rememberSaveable { mutableStateOf("") }
    var phase by remember { mutableFloatStateOf(0f) }
    val palette = listOf(Color(0xFFD67856), Color(0xFF507FAD), Color(0xFF558567))
    val names = listOf(t("朱色", "Orange", "橙色"), t("青", "Blue", "蓝色"), t("緑", "Green", "绿色"))
    LaunchedEffect(paused) {
        while (!paused) { delay(32); phase += .04f }
    }
    LaunchedEffect(recording) {
        while (recording) { delay(1000); recordingSeconds++ }
    }
    fun toggle(value: String) { panel = if (panel == value) "" else value }
    fun restore() { diameter = savedDiameter; ink = savedInk; confirmRestore = false; message = t("保存した状態に戻しました。", "Saved state restored.", "已恢复保存的状态。") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("練習用プレビュー", "Practice preview", "练习预览"), style = MaterialTheme.typography.titleSmall)
            Canvas(Modifier.fillMaxWidth().height(if (LocalConfiguration.current.screenHeightDp < 480) 110.dp else 160.dp).testTag("guide-preview")
                .semantics { contentDescription = "${diameter.toInt()} / ${names[ink]}" }
                .clickable { ink = (ink + 1) % palette.size }) {
                drawRect(Color(0xFFF2EFE7))
                val cx = size.width / 2f + sin(phase) * size.width * 0.12f
                val cy = size.height / 2f
                val coreR = size.minDimension * diameter / 600f
                val orbitR = (coreR * 1.45f).coerceAtMost(size.minDimension * 0.45f)
                drawCircle(Color(0xFF2B2B2B).copy(alpha = 0.14f), radius = orbitR, center = Offset(cx, cy), style = Stroke(width = 1.5.dp.toPx()))
                drawCircle(palette[ink], radius = coreR, center = Offset(cx, cy))
                for (i in 0 until 6) {
                    val theta = phase * 0.7f + (i * 2f * PI.toFloat() / 6f)
                    drawCircle(
                        palette[(ink + 1) % palette.size].copy(alpha = 0.8f),
                        radius = 4.dp.toPx(),
                        center = Offset(cx + cos(theta) * orbitR, cy + sin(theta) * orbitR)
                    )
                }
            }
            Text(if (paused) t("一時停止中", "Paused", "已暂停") else t("実行中 · 円をタップすると色が変わります", "Running · tap the circle to change color", "运行中 · 点击圆更改颜色"), style = MaterialTheme.typography.bodySmall)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                val colors = MaterialTheme.colorScheme
                val controlTranslator: (String, Array<out Any?>) -> String = { source, _ ->
                    when (source) {
                        "再生" -> t("再開", "Resume", "继续")
                        "一時停止" -> t("一時停止", "Pause", "暂停")
                        "再読み込み", "変更を実行" -> t("変更を実行", "Run changes", "运行更改")
                        "ログ" -> t("ログ", "Logs", "日志")
                        "パラメータ" -> t("パラメータ", "Parameters", "参数")
                        "プレビュー操作" -> t("プレビュー操作", "Preview actions", "预览操作")
                        "履歴" -> t(source, "History", "历史")
                        "スナップショット" -> t(source, "Snapshots", "快照")
                        "保存済み状態に戻す" -> t(source, "Revert to saved", "恢复到已保存状态")
                        else -> t(source, "Save all files", "保存所有文件")
                    }
                }
                RunStatusControls(
                    isError = false,
                    isPaused = paused,
                    colors = colors,
                    height = 38.dp,
                    buttonSize = 32.dp,
                    iconSize = 17.dp,
                    onTogglePause = { paused = !paused },
                    onReload = {
                        phase = 0f
                        paused = false
                        message = t("再実行しました。", "Restarted.", "已重新运行。")
                    },
                    textTranslator = controlTranslator
                )
                ConsoleButton(
                    hasError = false,
                    entryCount = 2,
                    colors = colors,
                    height = 38.dp,
                    minWidth = 38.dp,
                    iconSize = 17.dp,
                    onClick = { toggle("logs") },
                    textTranslator = controlTranslator
                )
                ParameterButton(
                    parameterCount = 2,
                    colors = colors,
                    height = 38.dp,
                    minWidth = 38.dp,
                    iconSize = 17.dp,
                    onClick = { toggle("parameters") },
                    textTranslator = controlTranslator,
                    active = panel == "parameters"
                )
                PreviewActionsToggleButton(
                    isRecordingOrCountingDown = recording,
                    previewActionsExpanded = panel == "capture",
                    isLandscape = false,
                    colors = colors,
                    onClick = { toggle("capture") },
                    textTranslator = controlTranslator
                )
                SaveRestoreControls(
                    hasUnsavedChanges = diameter != savedDiameter || ink != savedInk,
                    colors = colors,
                    height = 38.dp,
                    buttonSize = 32.dp,
                    iconSize = 18.dp,
                    onSnapshot = { panel = "snapshots" },
                    onRestore = { confirmRestore = true },
                    onSave = {
                        savedDiameter = diameter
                        savedInk = ink
                        message = t("練習用の状態を保存しました。", "Practice state saved.", "已保存练习状态。")
                    },
                    textTranslator = controlTranslator
                )
            }
            if (message.isNotEmpty()) Text(message, Modifier.testTag("guide-feedback"), style = MaterialTheme.typography.bodySmall)
            if (panel.isNotEmpty()) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(when (panel) {
                        "parameters" -> t("パラメータ", "Parameters", "参数")
                        "logs" -> t("ログ", "Logs", "日志")
                        "snapshots" -> t("スナップショット", "Snapshots", "快照")
                        else -> t("プレビュー操作", "Preview actions", "预览操作")
                    }, style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { panel = "" }) { Icon(painterResource(R.drawable.ic_close), t("パネルを閉じる", "Close panel", "关闭面板")) }
                }
                when (panel) {
                    "parameters" -> {
                        Text(t("直径", "Diameter", "直径") + " ${diameter.toInt()}", Modifier.testTag("guide-diameter"))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { diameter = (diameter - 10).coerceAtLeast(20f) }) { Text("−") }
                            StudioSlider(diameter, { diameter = it }, Modifier.weight(1f).testTag("guide-slider"), steps = 21, valueRange = 20f..240f)
                            TextButton(onClick = { diameter = (diameter + 10).coerceAtMost(240f) }) { Text("+") }
                        }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            names.forEachIndexed { index, name -> FilterChip(ink == index, { ink = index }, label = { Text(name) }) }
                        }
                        TextButton(onClick = { diameter = 120f; ink = 0 }) { Text(t("初期値に戻す", "Reset parameters", "重置参数")) }
                    }
                    "logs" -> Text("[demo] rinParams.diameter = ${diameter.toInt()}\n[demo] rinParams.ink = ${names[ink]}")
                    "snapshots" -> {
                        TextButton(onClick = { snapshotDiameter = diameter; snapshotInk = ink; hasSnapshot = true }) { Text(t("スナップショットを作成", "Create snapshot", "创建快照")) }
                        if (hasSnapshot) {
                            Text("${snapshotDiameter.toInt()} / ${names[snapshotInk]}")
                            TextButton(onClick = { diameter = snapshotDiameter; ink = snapshotInk; message = t("スナップショットを復元しました。", "Snapshot restored.", "已恢复快照。") }) { Text(t("この状態を復元", "Restore snapshot", "恢复此状态")) }
                        } else Text(t("まだスナップショットはありません。", "No snapshots yet.", "暂无快照。"))
                    }
                    "capture" -> {
                        TextButton(onClick = { captured = true; captureDiameter = diameter; captureInk = ink }) { Text(t("スクリーンショットを試す", "Try screenshot", "试用截图")) }
                        TextButton(onClick = {
                            if (recording) recordedSeconds = recordingSeconds else recordingSeconds = 0
                            recording = !recording
                        }) { Text(if (recording) t("録画を停止", "Stop recording", "停止录制") else t("録画を試す", "Try recording", "试用录制")) }
                        if (recording) Text(t("録画中（練習）", "Recording (practice)", "录制中（练习）") + " ${recordingSeconds}s")
                        if (!recording && recordedSeconds >= 0) Text(t("録画を終了しました（練習）", "Recording finished (practice)", "录制结束（练习）") + " ${recordedSeconds}s")
                        if (captured) {
                            Text(t("撮影結果（練習）", "Captured image (practice)", "截图结果（练习）"))
                            Canvas(Modifier.fillMaxWidth().height(80.dp)) { drawRect(Color(0xFFF2EFE7)); drawCircle(palette[captureInk], size.minDimension * captureDiameter / 600f) }
                        }
                    }
                }
            }
        }
    }
    if (confirmRestore) AlertDialog(onDismissRequest = { confirmRestore = false },
        title = { Text(t("保存した状態に戻しますか？", "Restore saved state?", "恢复保存的状态？")) },
        text = { Text(t("練習中の変更を、最後に保存した大きさと色に戻します。", "Replace practice changes with the last saved size and color.", "将练习中的修改恢复为上次保存的大小和颜色。")) },
        confirmButton = { TextButton(onClick = { restore() }) { Text(t("復元", "Restore", "恢复")) } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(t("キャンセル", "Cancel", "取消")) } })
}

@Composable
internal fun GuideBlockModeIllustration(language: String) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    var showingBlocks by rememberSaveable { mutableStateOf(true) }
    var autoRun by rememberSaveable { mutableStateOf(true) }
    var paletteOpen by rememberSaveable { mutableStateOf(false) }
    var fullPreviewOpen by rememberSaveable { mutableStateOf(false) }
    var inkIndex by rememberSaveable { mutableIntStateOf(0) }
    var circleSize by rememberSaveable { mutableIntStateOf(120) }
    var hasExtraRing by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf("") }

    val inks = listOf(Color(0xFFD67856), Color(0xFF3F6F8F), Color(0xFF4F7D5C), Color(0xFFE5A93B))
    val inkHexes = listOf("#D67856", "#3F6F8F", "#4F7D5C", "#E5A93B")

    val chromeModel = remember(paletteOpen) {
        BlockEditorChromeModel().apply {
            state = BlockChromeState(editable = true, busy = false, palette = paletteOpen)
        }
    }
    val uiTranslator: (String, Array<out Any?>) -> String = { source, args ->
        val translated = translateUi(source, language)
        if (args.isEmpty()) translated else String.format(java.util.Locale.ROOT, translated, *args)
    }

    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorDocumentBar(
                files = listOf("sketch.js"),
                selectedFile = "sketch.js",
                dirtyFiles = if (hasExtraRing || inkIndex != 0 || circleSize != 120) setOf("sketch.js") else emptySet(),
                showingBlocks = showingBlocks,
                allowBlocks = true,
                enabled = true,
                readOnly = false,
                autoRunEnabled = autoRun,
                previewHasChanges = !autoRun,
                onSelectFile = {},
                onSelectBlocks = { next ->
                    showingBlocks = next
                    feedback = if (next) {
                        t("ブロックモードへ切り替えました。", "Switched to Block mode.", "已切换到积木模式。")
                    } else {
                        t("コード表示へ切り替えました。元のコードがそのまま保たれます。", "Switched to Code view. Your source code stays intact.", "已切换到代码视图，原始代码保持完整。")
                    }
                },
                onAddFile = null,
                onToggleAutoRun = { autoRun = !autoRun },
                onRunChanges = { feedback = t("変更を実行しました。", "Ran changes.", "已运行更改。") },
                textTranslator = uiTranslator,
                onOpenFullPreview = {
                    fullPreviewOpen = true
                    feedback = t("全画面プレビューを開きました。", "Opened full-screen preview.", "已打开全屏预览。")
                }
            )

            if (fullPreviewOpen) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF2EFE7),
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    modifier = Modifier.fillMaxWidth().height(210.dp).testTag("guide-block-full-preview")
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(inks[inkIndex], radius = size.minDimension * (circleSize / 320f))
                            if (hasExtraRing) {
                                drawCircle(
                                    color = inks[(inkIndex + 1) % inks.size],
                                    radius = size.minDimension * 0.42f,
                                    style = Stroke(width = 4.dp.toPx())
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { fullPreviewOpen = false },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("全画面表示を閉じる", "Exit full screen", "退出全屏"), maxLines = 1)
                        }
                    }
                }
            } else if (!showingBlocks) {
                val codeText = buildString {
                    append("function setup() {\n  createCanvas(400, 400);\n}\n\nfunction draw() {\n  background('#F2EFE7');\n")
                    append("  fill('${inkHexes[inkIndex]}');\n")
                    append("  circle(200, 200, $circleSize);\n")
                    if (hasExtraRing) append("  circle(200, 200, 220);\n")
                    append("}")
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = codeText,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceContainer)
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFD97724),
                            contentColor = Color.White
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("function draw()", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Surface(
                                    onClick = {
                                        inkIndex = (inkIndex + 1) % inks.size
                                        feedback = t("スウォッチをタップして色を変更しました。", "Tapped the inline swatch to change color.", "已点击行内色块切换颜色。")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF8B5CF6),
                                    contentColor = Color.White
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(t("塗り色", "fill", "填充色"), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Box(
                                            Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(inks[inkIndex])
                                                .border(1.5.dp, Color.White, CircleShape)
                                        )
                                        Text(inkHexes[inkIndex], style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                    }
                                }
                                Surface(
                                    onClick = {
                                        circleSize = if (circleSize >= 160) 80 else circleSize + 40
                                        feedback = t("カプセルをタップして直径を $circleSize に変更しました。", "Tapped capsule to set diameter to $circleSize.", "已点击数值胶囊将直径设为 $circleSize。")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF2E8B8B),
                                    contentColor = Color.White
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(t("円を描く", "circle", "绘制圆"), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.22f)) {
                                            Text("200, 200", Modifier.padding(horizontal = 7.dp, vertical = 2.dp), fontSize = 11.sp, color = Color.White)
                                        }
                                        Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.32f)) {
                                            Text("$circleSize", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                                if (hasExtraRing) {
                                    Surface(
                                        onClick = { hasExtraRing = false },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF2E8B8B),
                                        contentColor = Color.White
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(t("外側のリング (タップで削除)", "Outer ring (tap to remove)", "外圈圆环 (点击移除)"), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (paletteOpen) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.surface,
                                border = BorderStroke(1.dp, colors.outlineVariant),
                                shadowElevation = 4.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        t("挿入先: draw() の末尾", "Insert at: end of draw()", "插入位置：draw() 末尾"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    OutlinedCard(
                                        onClick = {
                                            hasExtraRing = true
                                            paletteOpen = false
                                            feedback = t("ブロックを追加しました。", "Block inserted.", "已添加积木。")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(t("外側のリングを追加", "Add outer ring", "添加外圈圆环"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                Text("circle(200, 200, 220)", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant)
                                            }
                                            Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        shadowElevation = 4.dp,
                        modifier = Modifier.align(Alignment.TopEnd).size(width = 104.dp, height = 88.dp)
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Row(
                                Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("AUTO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                                Icon(
                                    painter = painterResource(R.drawable.ic_fullscreen),
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp).clickable { fullPreviewOpen = true }
                                )
                            }
                            Canvas(Modifier.fillMaxWidth().weight(1f).background(Color(0xFFF2EFE7))) {
                                drawCircle(inks[inkIndex], radius = size.minDimension * (circleSize / 320f))
                                if (hasExtraRing) {
                                    drawCircle(
                                        color = inks[(inkIndex + 1) % inks.size],
                                        radius = size.minDimension * 0.42f,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }

                BlockEditorFloatingDock(
                    model = chromeModel,
                    ready = true,
                    fullscreen = true,
                    canUndo = hasExtraRing || inkIndex != 0 || circleSize != 120,
                    canRedo = false,
                    text = { uiTranslator(it, emptyArray()) },
                    onCommand = { cmd ->
                        when (cmd) {
                            "add" -> paletteOpen = true
                            "dismiss" -> paletteOpen = false
                            "undo" -> {
                                hasExtraRing = false
                                inkIndex = 0
                                circleSize = 120
                                feedback = t("元に戻しました。", "Undone.", "已撤销。")
                            }
                        }
                    },
                    onSearch = {},
                    onDisplay = {},
                    onFullscreen = { fullPreviewOpen = !fullPreviewOpen }
                )
            }

            if (feedback.isNotEmpty()) {
                Text(feedback, style = MaterialTheme.typography.bodySmall, color = colors.primary)
            }
        }
    }
}
