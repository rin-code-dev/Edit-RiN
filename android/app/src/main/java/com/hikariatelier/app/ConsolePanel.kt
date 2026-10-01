package com.hikariatelier.app

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect

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
    val clipboard = LocalClipboardManager.current
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var followLatest by rememberSaveable { mutableStateOf(true) }
    var followingScroll by remember { mutableStateOf(false) }
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    val entries = visibleConsoleEntries(viewModel.entries, viewModel.errorsOnly)
    LaunchedEffect(dragging) {
        if (dragging) followLatest = false
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress && !followingScroll }.collect { browsing ->
            if (browsing) followLatest = false
        }
    }
    LaunchedEffect(entries.lastOrNull()?.id, entries.lastOrNull()?.count, viewModel.errorsOnly, followLatest) {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp)
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONSOLE",
                        modifier = Modifier.pointerInput(Unit) {
                            detectVerticalDragGestures { change, amount ->
                                change.consume()
                                viewModel.consoleHeight = (viewModel.consoleHeight - amount / density).coerceIn(110f, 600f)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.7.sp,
                        color = colors.onSurfaceVariant
                    )

                    TextButton(modifier = Modifier.size(32.dp), contentPadding = PaddingValues(0.dp), onClick = {
                        viewModel.consoleExpanded = !viewModel.consoleExpanded
                        viewModel.consoleHeight = if (viewModel.consoleExpanded) configuration.screenHeightDp * 0.6f else 170f
                    }) {
                        Text(
                            if (viewModel.consoleExpanded) "−" else "+",
                            modifier = Modifier.semantics {
                                contentDescription = uiText("コンソールの高さを変更")
                            }
                        )
                    }

                    val errorCount = viewModel.errorCount
                    if (errorCount > 0 || viewModel.errorsOnly) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            modifier = Modifier.height(32.dp).semantics {
                                contentDescription = uiText(if (viewModel.errorsOnly) "すべてのログを表示" else "エラーだけ表示")
                                selected = viewModel.errorsOnly
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                            onClick = { viewModel.errorsOnly = !viewModel.errorsOnly; followLatest = true }
                        ) {
                            Text("$errorCount ERR" + if (viewModel.errorsOnly) " ✓" else "",
                                style = MaterialTheme.typography.labelSmall, fontFamily = codeFontFamily)
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    IconButton(
                        modifier = Modifier.size(32.dp),
                        enabled = viewModel.entries.isNotEmpty(),
                        onClick = { clipboard.setText(AnnotatedString(viewModel.entries.joinToString("\n") { it.copyText() })) }
                    ) { Icon(painterResource(R.drawable.ic_console_copy), contentDescription = uiText("ログをすべてコピー"), modifier = Modifier.size(16.dp)) }
                    IconButton(modifier = Modifier.size(32.dp), onClick = { viewModel.clear() }) {
                        Icon(painterResource(R.drawable.ic_clear), contentDescription = uiText("ログを消去"), modifier = Modifier.size(16.dp))
                    }
                    IconButton(modifier = Modifier.size(32.dp), onClick = { viewModel.showConsole = false }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = uiText("閉じる"), modifier = Modifier.size(16.dp))
                    }

                }

            }

            HorizontalDivider(color = colors.outlineVariant)

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
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
                            max = (viewModel.consoleHeight.coerceAtMost(configuration.screenHeightDp * 0.65f) - 37f).coerceAtLeast(1f).dp
                        ),
                    contentPadding = PaddingValues(top = 4.dp, bottom = if (followLatest) 4.dp else 36.dp)
                ) {
                    items(items = entries, key = { it.id }, contentType = { "console_entry" }) { entry ->
                        val canNavigate = entry.line != null && entry.file != null &&
                            entry.workId == activeWorkId && (entry.file == "sketch.js" || entry.file in activeWorkFiles)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = canNavigate
                                ) {
                                    entry.line?.let { line ->
                                        entry.file?.let { file -> onNavigateToSource(file, line) }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = when (entry.level) {
                                    ConsoleLevel.ERROR -> "ERR"
                                    ConsoleLevel.WARNING -> "WARN"
                                    ConsoleLevel.LOG -> ">"
                                },
                                modifier = Modifier.width(38.dp),
                                fontFamily = codeFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when (entry.level) {
                                    ConsoleLevel.ERROR -> colors.error
                                    ConsoleLevel.WARNING -> colors.tertiary
                                    ConsoleLevel.LOG -> colors.onSurfaceVariant
                                }
                            )

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
                                color = colors.onSurface
                            )
                            entry.file?.let { file ->
                                Text(
                                    text = file + (entry.line?.let { ":$it" } ?: ""),
                                    fontFamily = codeFontFamily, fontSize = 10.sp,
                                    textDecoration = if (canNavigate) TextDecoration.Underline else TextDecoration.None,
                                    color = if (canNavigate) colors.primary else colors.onSurfaceVariant
                                )
                            }
                            }
                            IconButton(onClick = { clipboard.setText(AnnotatedString(entry.copyText())) }) {
                                Icon(painterResource(R.drawable.ic_copy), contentDescription = uiText("ログをコピー"), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                if (!followLatest) {
                    Surface(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 3.dp),
                        color = colors.surfaceContainerHigh, shape = RoundedCornerShape(8.dp)) {
                        TextButton(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp), onClick = {
                                followLatest = true
                            }) { Text(uiText("最新へ"), style = MaterialTheme.typography.labelSmall) }
                    }
                }
                }
            }
        }
    }
}
