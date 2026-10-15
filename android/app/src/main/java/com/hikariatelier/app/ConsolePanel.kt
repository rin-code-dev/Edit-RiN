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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
    modifier: Modifier = Modifier
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(
                min = 96.dp,
                max = viewModel.consoleHeight.coerceAtMost(configuration.screenHeightDp * 0.65f).dp
            )
            .border(
                width = 1.dp,
                color = colors.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
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

                        TextButton(
                            modifier = Modifier.size(30.dp),
                            contentPadding = PaddingValues(0.dp),
                            onClick = {
                                viewModel.consoleExpanded = !viewModel.consoleExpanded
                                viewModel.consoleHeight = if (viewModel.consoleExpanded) configuration.screenHeightDp * 0.6f else 170f
                            }
                        ) {
                            Text(
                                if (viewModel.consoleExpanded) "−" else "+",
                                modifier = Modifier.semantics {
                                    contentDescription = uiText("コンソールの高さを変更")
                                }
                            )
                        }

                        Spacer(Modifier.width(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                                Box(
                                    modifier = Modifier
                                        .height(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) filterColor.copy(alpha = 0.2f) else colors.surfaceContainerLow)
                                        .border(
                                            1.dp,
                                            if (isSelected) filterColor else colors.outlineVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            viewModel.filter = f
                                            followLatest = true
                                        }
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
                            onClick = { copyToClipboard(viewModel.entries.joinToString("\n") { it.copyText() }) }
                        ) {
                            Icon(painterResource(R.drawable.ic_console_copy), contentDescription = uiText("ログをすべてコピー"), modifier = Modifier.size(16.dp))
                        }
                        IconButton(modifier = Modifier.size(30.dp), onClick = { viewModel.clear() }) {
                            Icon(painterResource(R.drawable.ic_clear), contentDescription = uiText("ログを消去"), modifier = Modifier.size(16.dp))
                        }
                        IconButton(modifier = Modifier.size(30.dp), onClick = { viewModel.showConsole = false }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = uiText("閉じる"), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
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
                                max = (viewModel.consoleHeight.coerceAtMost(configuration.screenHeightDp * 0.65f) - 42f).coerceAtLeast(1f).dp
                            ),
                        contentPadding = PaddingValues(top = 4.dp, bottom = if (followLatest) 4.dp else 36.dp)
                    ) {
                        items(items = entries, key = { it.id }, contentType = { "console_entry" }) { entry ->
                            val canNavigate = entry.line != null && entry.file != null &&
                                entry.workId == activeWorkId && (entry.file == "sketch.js" || entry.file in activeWorkFiles)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
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
        }
    }
}
