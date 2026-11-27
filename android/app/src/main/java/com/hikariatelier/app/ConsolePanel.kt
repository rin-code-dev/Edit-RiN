package com.hikariatelier.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConsolePanel(
    viewModel: ConsoleViewModel,
    activeWorkId: String,
    activeWorkFiles: Map<String, String>,
    onNavigateToSource: (String, Int) -> Unit,
    codeFontFamily: FontFamily,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier,
    onEvaluateExpression: ((String) -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val copyToClipboard = rememberClipboardCopy()
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var followLatest by rememberSaveable { mutableStateOf(true) }
    var followingScroll by remember { mutableStateOf(false) }
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    val entries = visibleConsoleEntries(viewModel.entries, viewModel.filter)

    LaunchedEffect(dragging) {
        if (dragging) followLatest = false
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress && !followingScroll }.collect { browsing ->
            if (browsing) followLatest = false
        }
    }
    LaunchedEffect(entries.lastOrNull()?.id, entries.lastOrNull()?.count, viewModel.filter, followLatest) {
        if (followLatest && !dragging && entries.isNotEmpty()) {
            followingScroll = true
            try { listState.scrollToItem(entries.lastIndex) }
            finally { followingScroll = false }
        }
    }
    fun uiText(source: String, vararg arguments: Any?): String =
        textTranslator(source, arguments)

    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val effectiveMaxHeightDp = (if (isLandscape && !viewModel.consoleExpanded)
        viewModel.consoleHeight.coerceAtMost(132f)
    else viewModel.consoleHeight).coerceAtMost(configuration.screenHeightDp * (if (isLandscape) 0.45f else 0.65f))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(
                min = if (isLandscape) 72.dp else 96.dp,
                max = effectiveMaxHeightDp.dp
            )
            .border(
                width = UNIT_BORDER_WIDTH,
                color = colors.outlineVariant,
                shape = UNIT_CARD_SHAPE
            ),
        shape = UNIT_CARD_SHAPE,
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, amount ->
                                change.consume()
                                viewModel.consoleHeight = (viewModel.consoleHeight - amount / density).coerceIn(110f, 600f)
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(colors.outlineVariant.copy(alpha = 0.8f))
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 32.dp)
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONSOLE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.7.sp,
                            color = colors.onSurfaceVariant
                        )

                        IconButton(
                            modifier = Modifier.size(30.dp),
                            shape = UNIT_COMPACT_CONTROL_SHAPE,
                            onClick = {
                                viewModel.consoleExpanded = !viewModel.consoleExpanded
                                viewModel.consoleHeight = if (viewModel.consoleExpanded) configuration.screenHeightDp * 0.6f else 170f
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_down),
                                contentDescription = uiText("コンソールの高さを変更"),
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(if (viewModel.consoleExpanded) 180f else 0f)
                            )
                        }

                        Spacer(Modifier.width(8.dp))
                        Row(
                            modifier = Modifier
                                .clip(UNIT_COMPACT_CONTROL_SHAPE)
                                .background(colors.surfaceContainerLow)
                                .border(
                                    UNIT_BORDER_WIDTH,
                                    colors.outlineVariant.copy(alpha = 0.6f),
                                    UNIT_COMPACT_CONTROL_SHAPE
                                )
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                ConsoleFilter.ALL to "ALL",
                                ConsoleFilter.LOG to "LOG",
                                ConsoleFilter.WARN to "WARN",
                                ConsoleFilter.ERROR to "ERR"
                            ).forEach { (f, label) ->
                                val isSelected = viewModel.filter == f
                                val filterColor = when (f) {
                                    ConsoleFilter.ERROR -> colors.error
                                    ConsoleFilter.WARN -> colors.tertiary
                                    ConsoleFilter.LOG -> colors.primary
                                    ConsoleFilter.ALL -> colors.onSurface
                                }
                                val segmentShape = RoundedCornerShape(6.dp)
                                Box(
                                    modifier = Modifier
                                        .height(22.dp)
                                        .clip(segmentShape)
                                        .background(if (isSelected) filterColor.copy(alpha = 0.16f) else Color.Transparent)
                                        .then(
                                            if (isSelected) Modifier.border(
                                                UNIT_BORDER_WIDTH,
                                                filterColor.copy(alpha = 0.6f),
                                                segmentShape
                                            ) else Modifier
                                        )
                                        .clickable {
                                            viewModel.filter = f
                                            followLatest = true
                                        }
                                        .semantics { selected = isSelected }
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (f == ConsoleFilter.ERROR && viewModel.errorCount > 0) "${viewModel.errorCount} $label" else label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontFamily = codeFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) filterColor else colors.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        IconButton(
                            modifier = Modifier.size(30.dp),
                            enabled = viewModel.entries.isNotEmpty(),
                            shape = UNIT_COMPACT_CONTROL_SHAPE,
                            onClick = { copyToClipboard(viewModel.entries.joinToString("\n") { it.copyText() }) }
                        ) {
                            Icon(painterResource(R.drawable.ic_console_copy), contentDescription = uiText("ログをすべてコピー"), modifier = Modifier.size(16.dp))
                        }
                        IconButton(modifier = Modifier.size(30.dp), shape = UNIT_COMPACT_CONTROL_SHAPE, onClick = { viewModel.clear() }) {
                            Icon(painterResource(R.drawable.ic_clear), contentDescription = uiText("ログを消去"), modifier = Modifier.size(16.dp))
                        }
                        IconButton(modifier = Modifier.size(30.dp), shape = UNIT_COMPACT_CONTROL_SHAPE, onClick = { viewModel.showConsole = false }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = uiText("閉じる"), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = UNIT_HAIRLINE_ALPHA))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 40.dp else 56.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = uiText(if (viewModel.errorsOnly) "エラーはありません" else "ログはありません"),
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        fontFamily = codeFontFamily
                    )
                }
            } else {
                Box {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                max = (effectiveMaxHeightDp - if (onEvaluateExpression != null) 82f else 42f).coerceAtLeast(1f).dp
                            ),
                        contentPadding = PaddingValues(top = 4.dp, bottom = if (followLatest) 4.dp else 36.dp)
                    ) {
                        items(items = entries, key = { it.id }, contentType = { "console_entry" }) { entry ->
                            val canNavigate = entry.line != null && entry.file != null &&
                                entry.workId == activeWorkId && (entry.file == "sketch.js" || entry.file in activeWorkFiles)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                                    .clip(UNIT_COMPACT_CONTROL_SHAPE)
                                    .combinedClickable(
                                        onClick = {
                                            if (canNavigate) {
                                                entry.line?.let { line ->
                                                    entry.file?.let { file -> onNavigateToSource(file, line) }
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            copyToClipboard(entry.copyText())
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .padding(top = 2.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    when (entry.level) {
                                        ConsoleLevel.ERROR -> Box(
                                            Modifier.size(6.dp).clip(CircleShape).background(colors.error)
                                        )
                                        ConsoleLevel.WARNING -> Box(
                                            Modifier.size(6.dp).clip(CircleShape).background(colors.tertiary)
                                        )
                                        ConsoleLevel.LOG -> Text(
                                            text = "›",
                                            fontFamily = codeFontFamily,
                                            fontSize = 11.sp,
                                            color = colors.onSurfaceVariant.copy(alpha = 0.65f)
                                        )
                                    }
                                }

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = buildString {
                                            append(entry.message)
                                            if (entry.count > 1) {
                                                append("  ×${entry.count}")
                                            }
                                        },
                                        fontFamily = codeFontFamily,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = if (entry.level == ConsoleLevel.ERROR) colors.error else colors.onSurface
                                    )
                                }

                                entry.file?.let { file ->
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = file + (entry.line?.let { ":$it" } ?: ""),
                                        fontFamily = codeFontFamily,
                                        fontSize = 10.sp,
                                        textDecoration = if (canNavigate) TextDecoration.Underline else TextDecoration.None,
                                        color = if (canNavigate) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                    if (!followLatest) {
                        Surface(
                            Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 3.dp),
                            color = colors.surfaceContainerHigh,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            TextButton(
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp),
                                onClick = { followLatest = true }
                            ) {
                                Text(uiText("最新へ"), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            if (onEvaluateExpression != null) {
                var replText by rememberSaveable { mutableStateOf("") }
                var historyIndex by remember { mutableIntStateOf(-1) }
                val history = viewModel.replHistory
                fun submitRepl() {
                    val trimmed = replText.trim()
                    if (trimmed.isEmpty()) return
                    followLatest = true
                    historyIndex = -1
                    replText = ""
                    onEvaluateExpression(trimmed)
                }

                HorizontalDivider(color = colors.outlineVariant.copy(alpha = UNIT_HAIRLINE_ALPHA))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp)
                        .background(colors.surfaceContainerLowest)
                        .padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = ">",
                        fontFamily = codeFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (replText.isEmpty()) {
                            Text(
                                text = uiText("式を実行 (例: frameCount, width)"),
                                fontFamily = codeFontFamily,
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = replText,
                            onValueChange = {
                                replText = it
                                historyIndex = -1
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = codeFontFamily,
                                fontSize = 12.sp,
                                color = colors.onSurface
                            ),
                            cursorBrush = SolidColor(colors.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submitRepl() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (history.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(UNIT_COMPACT_CONTROL_SHAPE)
                                .clickable {
                                    val nextIdx = if (historyIndex < 0) history.lastIndex else (historyIndex - 1).coerceAtLeast(0)
                                    historyIndex = nextIdx
                                    replText = history[nextIdx]
                                }
                                .semantics { contentDescription = uiText("前のコマンド") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↑",
                                fontFamily = codeFontFamily,
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(UNIT_COMPACT_CONTROL_SHAPE)
                                .clickable(enabled = historyIndex >= 0) {
                                    if (historyIndex in 0 until history.lastIndex) {
                                        historyIndex += 1
                                        replText = history[historyIndex]
                                    } else {
                                        historyIndex = -1
                                        replText = ""
                                    }
                                }
                                .semantics { contentDescription = uiText("次のコマンド") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↓",
                                fontFamily = codeFontFamily,
                                fontSize = 12.sp,
                                color = if (historyIndex >= 0) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }
                    }
                    IconButton(
                        onClick = { submitRepl() },
                        enabled = replText.isNotBlank(),
                        shape = UNIT_COMPACT_CONTROL_SHAPE,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play),
                            contentDescription = uiText("式を実行"),
                            tint = if (replText.isNotBlank()) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
