package com.hikariatelier.app

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikariatelier.app.ui.theme.LocalAppThemeMode
import com.hikariatelier.app.ui.theme.codeSyntaxPalette

private val ORDERED_PREVIEW_RATIOS = listOf(
    "1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "4:5", "device", "device_landscape", "custom"
)

@Composable
internal fun AddWorkDialog(
    worksCount: Int,
    workTemplates: List<WorkTemplate>,
    wideWorkPanels: Boolean,
    colors: ColorScheme,
    codeFontFamily: FontFamily,
    configuration: Configuration,
    onDismiss: () -> Unit,
    onCreate: (
        title: String,
        ratio: String,
        sizingMode: CanvasSizingMode,
        template: WorkTemplate,
        openInBlockMode: Boolean,
        folderName: String
    ) -> Unit,
    userTemplates: List<Work>,
    busy: Boolean,
    templateLoadFailed: Boolean,
    onRetryTemplates: () -> Unit,
    onCreateFromTemplate: (
        title: String,
        templateId: String,
        openInBlockMode: Boolean,
        folderName: String
    ) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onManageTemplates: (() -> Unit)? = null,
    initialCategory: WorkTemplateCategory = WorkTemplateCategory.BASIC,
    initialFolder: String = "",
    galleryFolders: List<String> = emptyList(),
    onImportJs: (() -> Unit)? = null,
    onImportZip: (() -> Unit)? = null,
    onImportP5: (() -> Unit)? = null,
    textTranslator: (String, Array<out Any?>) -> String,
    windowSetup: @Composable () -> Unit = {}
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var newTitle by rememberSaveable {
        mutableStateOf(uiText("新しい作品") + " ${worksCount + 1}")
    }
    var categoryName by rememberSaveable(initialCategory) { mutableStateOf(initialCategory.name) }
    val category = runCatching { WorkTemplateCategory.valueOf(categoryName) }.getOrDefault(WorkTemplateCategory.BASIC)
    val defaultKind = WorkTemplateKind.entries.firstOrNull { it.category == category } ?: WorkTemplateKind.BASIC_2D
    var kindName by rememberSaveable(initialCategory) { mutableStateOf(defaultKind.name) }
    val kind = runCatching { WorkTemplateKind.valueOf(kindName) }.getOrDefault(defaultKind)

    var selectedTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    var userTemplateQuery by rememberSaveable { mutableStateOf("") }
    var deletingTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleUserTemplates = remember(userTemplates, userTemplateQuery) {
        matchingUserTemplates(userTemplates, userTemplateQuery)
    }
    val selectedUserTemplate = userTemplates.firstOrNull { it.id == selectedTemplateId }
        ?: visibleUserTemplates.firstOrNull()
        ?: userTemplates.firstOrNull()

    var userChoseMode by rememberSaveable(initialCategory) { mutableStateOf(false) }
    var startInBlockMode by rememberSaveable(initialCategory) {
        mutableStateOf(initialCategory == WorkTemplateCategory.BLOCKS)
    }

    LaunchedEffect(userTemplates, selectedTemplateId, category) {
        if (selectedTemplateId == null || userTemplates.none { it.id == selectedTemplateId }) {
            selectedTemplateId = userTemplates.firstOrNull()?.id
        }
        if (category == WorkTemplateCategory.USER && !userChoseMode) {
            val activeUserTemplate = userTemplates.firstOrNull { it.id == selectedTemplateId }
                ?: userTemplates.firstOrNull()
            startInBlockMode = activeUserTemplate?.blockEditorState != null
        }
    }

    var selectedFolder by rememberSaveable(initialFolder) {
        mutableStateOf(initialFolder.takeIf { it in galleryFolders }.orEmpty())
    }

    var stageTab by rememberSaveable { mutableStateOf("preview") }
    var importMenuExpanded by remember { mutableStateOf(false) }

    var newRatio by rememberSaveable { mutableStateOf("1:1") }
    var customWidth by rememberSaveable { mutableStateOf("800") }
    var customHeight by rememberSaveable { mutableStateOf("800") }
    var newCanvasModeName by rememberSaveable { mutableStateOf(CanvasSizingMode.FIXED.name) }
    val newCanvasMode = CanvasSizingMode.valueOf(newCanvasModeName)

    val maxSide = maxOf(configuration.screenWidthDp, configuration.screenHeightDp, 1)
    val minSide = minOf(configuration.screenWidthDp, configuration.screenHeightDp, 1)
    val scale = 960f / maxSide
    val portrait = WorkTemplate("device", (minSide * scale).toInt().coerceAtLeast(1), (maxSide * scale).toInt().coerceAtLeast(1))
    val landscape = WorkTemplate("device_landscape", portrait.height, portrait.width)
    val baseOptions = workTemplates + listOf(portrait, landscape)
    val optionsByRatio = baseOptions.associateBy { it.ratio }
    val customW = customWidth.toIntOrNull()
    val customH = customHeight.toIntOrNull()
    val validCustom = customCanvasDimensionsValid(customW, customH)
    val template = if (newRatio == "custom" && validCustom) {
        WorkTemplate(ratioFromDimensions(customW!!, customH!!), customW, customH, kind)
    } else {
        (optionsByRatio[newRatio] ?: workTemplates.first { it.ratio == "1:1" }).copy(kind = kind)
    }

    val darkTheme = colors.surface.luminance() < 0.5f
    val appThemeMode = LocalAppThemeMode.current
    val syntaxPalette = remember(appThemeMode, darkTheme, colors.surfaceContainerLowest) {
        codeSyntaxPalette(appThemeMode, darkTheme, colors.surfaceContainerLowest)
    }

    val hasImportActions = onImportJs != null || onImportZip != null || onImportP5 != null
    val studioMaxWidth = if (isLandscape) (configuration.screenWidthDp - 32).coerceIn(360, 960).dp else 600.dp

    fun selectCategory(next: WorkTemplateCategory) {
        categoryName = next.name
        if (next != WorkTemplateCategory.USER) {
            val currentMatches = kind.category == next
            val nextKind = if (currentMatches) kind else {
                WorkTemplateKind.entries.first { it.category == next }
            }
            kindName = nextKind.name
            if (!userChoseMode) {
                startInBlockMode = next == WorkTemplateCategory.BLOCKS
            }
        } else if (!userChoseMode) {
            startInBlockMode = selectedUserTemplate?.blockEditorState != null
        }
    }

    fun selectKind(option: WorkTemplateKind) {
        kindName = option.name
        if (!userChoseMode) {
            startInBlockMode = option.recommendedBlockMode
        }
    }

    val canCreate = !busy && if (category == WorkTemplateCategory.USER) {
        selectedUserTemplate != null && !templateLoadFailed
    } else {
        newRatio != "custom" || validCustom
    }

    val templateColumns = when {
        configuration.fontScale > 1.35f -> 1
        wideWorkPanels -> 2
        configuration.screenWidthDp < 340 -> 1
        else -> 2
    }

    @Composable
    fun CapabilityBadge(
        label: String,
        highlighted: Boolean = false
    ) {
        Surface(
            shape = UNIT_COMPACT_CONTROL_SHAPE,
            color = if (highlighted) colors.primary.copy(alpha = 0.12f) else colors.surfaceContainerHigh,
            contentColor = if (highlighted) colors.primary else colors.onSurfaceVariant,
            border = BorderStroke(
                UNIT_BORDER_WIDTH,
                if (highlighted) colors.primary.copy(alpha = 0.45f) else colors.outlineVariant.copy(alpha = 0.6f)
            )
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }

    @Composable
    fun CodePeekSurface(
        code: String,
        modifier: Modifier = Modifier
    ) {
        val highlighted = remember(code, darkTheme, syntaxPalette) {
            JavaScriptHighlighter(darkTheme = darkTheme, palette = syntaxPalette)
                .filter(AnnotatedString(code))
                .text
        }
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = UNIT_CONTROL_SHAPE,
            color = colors.surfaceContainerLowest,
            border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant.copy(alpha = 0.7f))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Text(
                    text = highlighted,
                    fontFamily = codeFontFamily,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = colors.onSurface
                )
            }
        }
    }

    @Composable
    fun BuiltinTemplateGrid(columns: Int) {
        val items = remember(category) { WorkTemplateKind.entries.filter { it.category == category } }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { option ->
                        val isSelected = kind == option
                        val optionTemplate = remember(option) { WorkTemplate("1:1", 400, 400, option) }
                        Surface(
                            onClick = { selectKind(option) },
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics {
                                    this.selected = isSelected
                                    role = Role.RadioButton
                                },
                            shape = UNIT_CARD_SHAPE,
                            color = if (isSelected) colors.primary.copy(alpha = 0.10f) else colors.surfaceContainerLow,
                            border = BorderStroke(
                                if (isSelected) UNIT_ACTIVE_BORDER_WIDTH else UNIT_BORDER_WIDTH,
                                if (isSelected) colors.primary else colors.outlineVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(11.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = uiText(option.title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) colors.primary else colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CapabilityBadge(option.badge, highlighted = isSelected)
                                }
                                Text(
                                    text = uiText(option.description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val extraTag = when {
                                    option.requiresSound -> "p5.sound"
                                    optionTemplate.libraries.containsKey("matter-js") -> "Matter.js"
                                    optionTemplate.libraries.containsKey("p5.brush") -> "p5.brush"
                                    option.recommendedBlockMode -> uiText("ブロック向け")
                                    else -> null
                                }
                                if (extraTag != null) {
                                    Text(
                                        text = extraTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) colors.primary else colors.outline,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    @Composable
    fun UserTemplatesPanel() {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (templateLoadFailed) {
                Surface(
                    shape = UNIT_CARD_SHAPE,
                    color = colors.errorContainer.copy(alpha = 0.45f),
                    border = BorderStroke(UNIT_BORDER_WIDTH, colors.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            uiText("テンプレートを読み込めませんでした。保存データは変更していません。"),
                            color = colors.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        FilledTonalButton(
                            enabled = !busy,
                            onClick = onRetryTemplates,
                            shape = UNIT_CONTROL_SHAPE,
                            colors = unitTonalButtonColors(colors)
                        ) {
                            Text(uiText("再試行"))
                        }
                    }
                }
            } else if (userTemplates.isEmpty()) {
                Surface(
                    shape = UNIT_CARD_SHAPE,
                    color = colors.surfaceContainerLow,
                    border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            uiText("自作テンプレートはまだありません"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            uiText("作品メニューの「テンプレートとして保存」から登録できます。"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            uiText("コード・補助ファイル・素材・実行設定をそのまま引き継ぎます。"),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.outline
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = userTemplateQuery,
                    onValueChange = { userTemplateQuery = it },
                    enabled = !busy,
                    singleLine = true,
                    shape = UNIT_CONTROL_SHAPE,
                    label = { Text(uiText("テンプレート名で検索")) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (visibleUserTemplates.isEmpty()) {
                    Text(
                        uiText("一致するテンプレートはありません"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    visibleUserTemplates.forEach { userTemplate ->
                        val isSelected = selectedUserTemplate?.id == userTemplate.id
                        Surface(
                            onClick = {
                                selectedTemplateId = userTemplate.id
                                if (!userChoseMode) {
                                    startInBlockMode = userTemplate.blockEditorState != null
                                }
                            },
                            enabled = !busy,
                            shape = UNIT_CARD_SHAPE,
                            color = if (isSelected) colors.primary.copy(alpha = 0.10f) else colors.surfaceContainerLow,
                            border = BorderStroke(
                                if (isSelected) UNIT_ACTIVE_BORDER_WIDTH else UNIT_BORDER_WIDTH,
                                if (isSelected) colors.primary else colors.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth().semantics {
                                this.selected = isSelected
                                role = Role.RadioButton
                            }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = userTemplate.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) colors.primary else colors.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${uiText("ファイル")} ${1 + userTemplate.files.size} · ${uiText("素材")} ${userTemplate.assets.size} · p5.js ${userTemplate.p5Version}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                    val badges = buildList {
                                        if (userTemplate.p5SoundEnabled) add("p5.sound")
                                        if (userTemplate.libraries.containsKey("matter-js")) add("Matter.js")
                                        if (userTemplate.libraries.containsKey("p5.brush")) add("p5.brush")
                                        if (userTemplate.blockEditorState != null) add(uiText("ブロック配置"))
                                    }
                                    if (badges.isNotEmpty()) {
                                        Row(
                                            Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            badges.forEach { badge -> CapabilityBadge(badge, highlighted = isSelected) }
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { deletingTemplateId = userTemplate.id },
                                    enabled = !busy,
                                    shape = UNIT_COMPACT_CONTROL_SHAPE
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_delete),
                                        uiText("削除"),
                                        Modifier.size(18.dp),
                                        tint = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            onManageTemplates?.let { manage ->
                OutlinedButton(
                    enabled = !busy,
                    onClick = manage,
                    shape = UNIT_CONTROL_SHAPE,
                    border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(painterResource(R.drawable.ic_folder_code), null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(uiText("テンプレート管理"))
                }
            }
        }
    }

    @Composable
    fun TemplateStageCard() {
        if (category == WorkTemplateCategory.USER) {
            selectedUserTemplate?.let { userTemplate ->
                Surface(
                    shape = UNIT_CARD_SHAPE,
                    color = colors.surfaceContainerLow,
                    border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = userTemplate.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            CapabilityBadge(uiText("コードプレビュー"))
                        }
                        CodePeekSurface(
                            code = userTemplate.code,
                            modifier = Modifier.height(if (isLandscape) 144.dp else 156.dp)
                        )
                    }
                }
            }
            return
        }

        val previewCode = remember(template, newCanvasMode) { template.code(newCanvasMode) }
        Surface(
            shape = UNIT_CARD_SHAPE,
            color = colors.surfaceContainerLow,
            border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = uiText(kind.title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CapabilityBadge(kind.badge, highlighted = true)
                            if (kind.requiresSound) CapabilityBadge("p5.sound")
                            if (template.libraries.containsKey("matter-js")) CapabilityBadge("Matter.js")
                            if (template.libraries.containsKey("p5.brush")) CapabilityBadge("p5.brush")
                            if (kind.recommendedBlockMode) CapabilityBadge(uiText("ブロック向け"))
                        }
                    }
                    Surface(
                        shape = UNIT_CONTROL_SHAPE,
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant)
                    ) {
                        Row(Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf("preview" to "見本", "code" to "コード").forEach { (tabId, tabLabel) ->
                                val active = stageTab == tabId
                                Box(
                                    modifier = Modifier
                                        .clip(UNIT_COMPACT_CONTROL_SHAPE)
                                        .background(if (active) colors.surface else Color.Transparent)
                                        .clickable(enabled = !busy) { stageTab = tabId }
                                        .semantics {
                                            selected = active
                                            role = Role.Tab
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = uiText(tabLabel),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (active) colors.primary else colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                val stageHeight = if (isLandscape) 168.dp else 176.dp
                if (stageTab == "code") {
                    CodePeekSurface(
                        code = previewCode,
                        modifier = Modifier.height(stageHeight)
                    )
                } else {
                    Surface(
                        shape = UNIT_CONTROL_SHAPE,
                        color = colors.surfaceContainer,
                        border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().height(stageHeight)
                    ) {
                        TemplateLivePreview(
                            kind = kind,
                            colors = colors,
                            text = { uiText(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Text(
                    text = uiText(kind.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                if (stageTab == "preview") {
                    Text(
                        text = uiText("動作見本（作品のサイズとは異なります）"),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.outline
                    )
                }
            }
        }
    }

    @Composable
    fun WorkSetupUnit() {
        Surface(
            shape = UNIT_CARD_SHAPE,
            color = colors.surfaceContainerLow,
            border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    enabled = !busy,
                    label = { Text(uiText("作品名")) },
                    singleLine = true,
                    shape = UNIT_CONTROL_SHAPE,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = uiText("開始する編集モード"),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(false to "コード", true to "ブロック").forEach { (isBlocks, label) ->
                            val selected = startInBlockMode == isBlocks
                            Surface(
                                onClick = {
                                    startInBlockMode = isBlocks
                                    userChoseMode = true
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f).semantics {
                                    this.selected = selected
                                    role = Role.RadioButton
                                },
                                shape = UNIT_CONTROL_SHAPE,
                                color = if (selected) colors.primary.copy(alpha = 0.12f) else colors.surface,
                                border = BorderStroke(
                                    if (selected) UNIT_ACTIVE_BORDER_WIDTH else UNIT_BORDER_WIDTH,
                                    if (selected) colors.primary else colors.outlineVariant
                                )
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(if (isBlocks) R.drawable.ic_snippet else R.drawable.ic_code),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (selected) colors.primary else colors.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = uiText(label),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) colors.primary else colors.onSurface
                                    )
                                }
                            }
                        }
                    }
                    if (startInBlockMode && category != WorkTemplateCategory.USER && !kind.blockFriendly) {
                        Text(
                            text = uiText("一部の構文はカスタムコードブロックとして表示されます"),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                if (galleryFolders.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = uiText("保存先フォルダー"),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (listOf("") + galleryFolders).forEach { folder ->
                                val isSelected = selectedFolder == folder
                                FilterChip(
                                    selected = isSelected,
                                    enabled = !busy,
                                    onClick = { selectedFolder = folder },
                                    label = { Text(if (folder.isEmpty()) uiText("未分類") else folder, maxLines = 1) },
                                    shape = UNIT_CONTROL_SHAPE,
                                    colors = unitFilterChipColors(colors),
                                    border = unitFilterChipBorder(isSelected, !busy, colors)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CanvasSettingsUnit() {
        if (category == WorkTemplateCategory.USER) return
        Surface(
            shape = UNIT_CARD_SHAPE,
            color = colors.surfaceContainerLow,
            border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = uiText("キャンバス設定"),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = template.canvasCallPreview(newCanvasMode),
                        fontFamily = codeFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ORDERED_PREVIEW_RATIOS.forEach { ratio ->
                        val isSelected = newRatio == ratio
                        val option = optionsByRatio[ratio]
                        val aspect = option?.let { it.width.toFloat() / it.height } ?: 1f
                        val frameSize = fitPreviewSize(22f, 22f, aspect)
                        Surface(
                            onClick = {
                                newRatio = ratio
                                if (ratio == "device" || ratio == "device_landscape") {
                                    newCanvasModeName = CanvasSizingMode.RESPONSIVE.name
                                } else if (ratio == "custom") {
                                    newCanvasModeName = CanvasSizingMode.FIXED.name
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.width(78.dp).semantics {
                                this.selected = isSelected
                                role = Role.RadioButton
                            },
                            shape = UNIT_CONTROL_SHAPE,
                            color = if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.surface,
                            border = BorderStroke(
                                if (isSelected) UNIT_ACTIVE_BORDER_WIDTH else UNIT_BORDER_WIDTH,
                                if (isSelected) colors.primary else colors.outlineVariant
                            )
                        ) {
                            Column(
                                Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                                    Box(
                                        Modifier
                                            .size(frameSize.width.dp, frameSize.height.dp)
                                            .border(
                                                1.5.dp,
                                                if (isSelected) colors.primary else colors.onSurfaceVariant,
                                                RoundedCornerShape(3.dp)
                                            )
                                    )
                                }
                                Text(
                                    text = when (ratio) {
                                        "device_landscape" -> uiText("端末・横")
                                        "device" -> uiText("端末・縦")
                                        "custom" -> uiText("自由指定")
                                        else -> ratio
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) colors.primary else colors.onSurface,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (option != null && ratio != "device" && ratio != "device_landscape") {
                                        "${option.width}×${option.height}"
                                    } else if (ratio == "custom") {
                                        "px"
                                    } else {
                                        uiText("画面")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CanvasSizingMode.FIXED, CanvasSizingMode.RESPONSIVE).forEach { mode ->
                        val isSelected = newCanvasMode == mode
                        val modeEnabled = !busy &&
                            (mode != CanvasSizingMode.FIXED || (newRatio != "device" && newRatio != "device_landscape")) &&
                            (mode != CanvasSizingMode.RESPONSIVE || newRatio != "custom")
                        Surface(
                            onClick = { newCanvasModeName = mode.name },
                            enabled = modeEnabled,
                            modifier = Modifier.weight(1f).semantics {
                                this.selected = isSelected
                                role = Role.RadioButton
                            },
                            shape = UNIT_CONTROL_SHAPE,
                            color = if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.surface,
                            border = BorderStroke(
                                if (isSelected) UNIT_ACTIVE_BORDER_WIDTH else UNIT_BORDER_WIDTH,
                                when {
                                    !modeEnabled -> colors.outlineVariant.copy(alpha = 0.4f)
                                    isSelected -> colors.primary
                                    else -> colors.outlineVariant
                                }
                            )
                        ) {
                            Text(
                                text = uiText(if (mode == CanvasSizingMode.FIXED) "固定サイズ" else "画面に合わせる"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = when {
                                    !modeEnabled -> colors.onSurfaceVariant.copy(alpha = 0.45f)
                                    isSelected -> colors.primary
                                    else -> colors.onSurface
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
                Text(
                    text = uiText(
                        if (newCanvasMode == CanvasSizingMode.FIXED) {
                            "指定した描画サイズを維持し、表示だけを枠に合わせます"
                        } else {
                            "選んだ比率はプレビュー枠に適用されます。キャンバスは画面に合わせて変化します。"
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )

                if (newRatio == "custom") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customWidth,
                            onValueChange = { customWidth = it },
                            enabled = !busy,
                            label = { Text(uiText("幅（px）")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = !validCustom,
                            shape = UNIT_CONTROL_SHAPE,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customHeight,
                            onValueChange = { customHeight = it },
                            enabled = !busy,
                            label = { Text(uiText("高さ（px）")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = !validCustom,
                            shape = UNIT_CONTROL_SHAPE,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = uiText(
                            if (validCustom) "入力した幅と高さからプレビュー比率を設定します。"
                            else "幅と高さは1〜8192の整数、合計1600万ピクセル以内にしてください。"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (validCustom) colors.onSurfaceVariant else colors.error
                    )
                }
            }
        }
    }

    UnitModal(
        onDismissRequest = { if (!busy) onDismiss() },
        dismissEnabled = !busy,
        maxWidth = studioMaxWidth,
        maxHeight = adaptiveSheetMaxHeight(portraitFraction = 0.92f),
        windowSetup = windowSetup
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = if (isLandscape) 6.dp else 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = uiText("新しい作品"),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = uiText(category.description),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hasImportActions) {
                    Box {
                        OutlinedButton(
                            onClick = { importMenuExpanded = true },
                            enabled = !busy,
                            shape = UNIT_CONTROL_SHAPE,
                            border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_import_js), null, Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(uiText("取り込む"), style = MaterialTheme.typography.labelMedium)
                        }
                        UnitDropdownMenu(
                            expanded = importMenuExpanded,
                            onDismissRequest = { importMenuExpanded = false }
                        ) {
                            onImportJs?.let { importJs ->
                                DropdownMenuItem(
                                    text = { Text(uiText("JSをインポート")) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_import_js), null, Modifier.size(18.dp)) },
                                    onClick = { importMenuExpanded = false; importJs() }
                                )
                            }
                            onImportZip?.let { importZip ->
                                DropdownMenuItem(
                                    text = { Text(uiText("作品ZIPを追加")) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_folder_code), null, Modifier.size(18.dp)) },
                                    onClick = { importMenuExpanded = false; importZip() }
                                )
                            }
                            onImportP5?.let { importP5 ->
                                DropdownMenuItem(
                                    text = { Text(uiText("p5.js Web Editorから取り込む")) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_code), null, Modifier.size(18.dp)) },
                                    onClick = { importMenuExpanded = false; importP5() }
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    shape = UNIT_CONTROL_SHAPE,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_close), uiText("閉じる"), Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WorkTemplateCategory.entries.forEach { tab ->
                    val isSelected = category == tab
                    val label = if (tab == WorkTemplateCategory.USER && userTemplates.isNotEmpty()) {
                        "${uiText(tab.label)} (${userTemplates.size})"
                    } else {
                        uiText(tab.label)
                    }
                    FilterChip(
                        selected = isSelected,
                        enabled = !busy,
                        onClick = { selectCategory(tab) },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        },
                        shape = UNIT_CONTROL_SHAPE,
                        colors = unitFilterChipColors(colors),
                        border = unitFilterChipBorder(isSelected, !busy, colors)
                    )
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = UNIT_HAIRLINE_ALPHA))

            if (wideWorkPanels) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        Modifier
                            .weight(0.52f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (category == WorkTemplateCategory.USER) {
                            UserTemplatesPanel()
                        } else {
                            BuiltinTemplateGrid(templateColumns)
                        }
                    }
                    VerticalDivider(color = colors.outlineVariant.copy(alpha = UNIT_HAIRLINE_ALPHA))
                    Column(
                        Modifier
                            .weight(0.48f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TemplateStageCard()
                        WorkSetupUnit()
                        CanvasSettingsUnit()
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 10.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TemplateStageCard()
                    if (category == WorkTemplateCategory.USER) {
                        UserTemplatesPanel()
                    } else {
                        BuiltinTemplateGrid(templateColumns)
                    }
                    WorkSetupUnit()
                    CanvasSettingsUnit()
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = UNIT_HAIRLINE_ALPHA))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isLandscape) 8.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val summary = if (category == WorkTemplateCategory.USER) {
                    selectedUserTemplate?.title ?: uiText("自作テンプレート")
                } else {
                    val modeText = uiText(if (startInBlockMode) "ブロック" else "コード")
                    val ratioText = if (newRatio == "custom" && !validCustom) "—"
                        else previewAspectRatioLabel(template.ratio) { uiText(it) }
                    "$modeText · $ratioText"
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = !busy,
                    onClick = onDismiss,
                    shape = UNIT_CONTROL_SHAPE
                ) {
                    Text(uiText("キャンセル"))
                }
                Button(
                    enabled = canCreate,
                    onClick = {
                        if (category == WorkTemplateCategory.USER) {
                            selectedUserTemplate?.let {
                                onCreateFromTemplate(newTitle, it.id, startInBlockMode, selectedFolder)
                            }
                        } else {
                            onCreate(newTitle, template.ratio, newCanvasMode, template, startInBlockMode, selectedFolder)
                        }
                    },
                    shape = UNIT_CONTROL_SHAPE
                ) {
                    Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(uiText("作成"))
                }
            }
        }
    }

    userTemplates.firstOrNull { it.id == deletingTemplateId }?.let { deleting ->
        UnitAlertDialog(
            onDismissRequest = { if (!busy) deletingTemplateId = null },
            title = { Text(uiText("テンプレートを削除")) },
            text = { Text(uiText("「%s」を削除します。作成済みの作品には影響しません。", deleting.title)) },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    shape = UNIT_CONTROL_SHAPE,
                    onClick = {
                        onDeleteTemplate(deleting.id)
                        deletingTemplateId = null
                    }
                ) {
                    Text(uiText("削除"), color = colors.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    shape = UNIT_CONTROL_SHAPE,
                    onClick = { deletingTemplateId = null }
                ) {
                    Text(uiText("キャンセル"))
                }
            }
        )
    }
}
