package com.hikariatelier.app

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val colors = MaterialTheme.colorScheme
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
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

                TextButton(onClick = {
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
                if (errorCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$errorCount ERR",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.error,
                        fontFamily = codeFontFamily
                    )
                }

                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = { viewModel.clear() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(text = "CLEAR", fontSize = 10.sp)
                }

                TextButton(
                    onClick = { viewModel.showConsole = false },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(text = "CLOSE", fontSize = 10.sp)
                }
            }

            HorizontalDivider(color = colors.outlineVariant)

            if (viewModel.entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = uiText("ログはありません"),
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        fontFamily = codeFontFamily
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            max = (viewModel.consoleHeight.coerceAtMost(configuration.screenHeightDp * 0.65f) - 40f).dp
                        ),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(items = viewModel.entries, key = { it.id }, contentType = { "console_entry" }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = entry.line != null && entry.file != null &&
                                        entry.workId == activeWorkId &&
                                        (entry.file == "sketch.js" || entry.file in activeWorkFiles)
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

                            Text(
                                text = buildString {
                                    append(entry.message)
                                    if (entry.count > 1) {
                                        append("  ×${entry.count}")
                                    }
                                    entry.line?.let {
                                        append("  ·  ${entry.file}:$it")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                fontFamily = codeFontFamily,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = colors.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
