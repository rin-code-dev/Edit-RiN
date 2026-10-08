package com.hikariatelier.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EditorAccessoryBar(
    editingValue: TextFieldValue,
    compactAccessoryKeys: Boolean,
    colors: ColorScheme,
    codeFontFamily: FontFamily,
    undoAvailable: Boolean,
    redoAvailable: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSearch: () -> Unit,
    onFormat: () -> Unit,
    onSnippets: () -> Unit,
    onReference: () -> Unit,
    onApplyEdit: (TextFieldValue) -> Unit,
    lastColorPickerColor: Color,
    onOpenColorPicker: (EditorColorTarget?, Color) -> Unit,
    showAccessoryNavigation: Boolean,
    showAccessorySymbols: Boolean,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    @Composable
    fun AccessoryKey(
        label: String,
        description: String,
        width: Int = 40,
        command: Boolean = false,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .width(
                    (if (compactAccessoryKeys) {
                        (width - 6).coerceAtLeast(34)
                    } else {
                        width
                    }).dp
                )
                .height(if (compactAccessoryKeys) 32.dp else 38.dp)
                .semantics {
                    contentDescription = description
                },
            shape = RoundedCornerShape(8.dp),
            color = if (command) {
                colors.secondaryContainer
            } else {
                colors.surface
            },
            contentColor = if (command) {
                colors.onSecondaryContainer.copy(
                    alpha = if (enabled) 1f else 0.38f
                )
            } else {
                colors.onSurface.copy(
                    alpha = if (enabled) 1f else 0.38f
                )
            },
            border = BorderStroke(
                1.dp,
                if (command) colors.secondary.copy(alpha = 0.42f)
                else colors.outlineVariant
            ),
            shadowElevation = 1.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontFamily = codeFontFamily,
                    fontSize = if (compactAccessoryKeys) {
                        if (label.length > 3) 10.sp else 12.sp
                    } else if (label.length > 3) 11.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    fun KeyDivider() {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .width(1.dp)
                .height(26.dp)
                .background(colors.outlineVariant)
        )
    }

    val moveLeft = {
        val target = if (!editingValue.selection.collapsed) {
            editingValue.selection.min
        } else {
            (editingValue.selection.start - 1).coerceAtLeast(0)
        }
        onApplyEdit(editingValue.copy(selection = TextRange(target)))
    }
    val moveRight = {
        val target = if (!editingValue.selection.collapsed) {
            editingValue.selection.max
        } else {
            (editingValue.selection.end + 1).coerceAtMost(editingValue.text.length)
        }
        onApplyEdit(editingValue.copy(selection = TextRange(target)))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(
                    horizontal = 8.dp,
                    vertical = if (compactAccessoryKeys) 5.dp else 7.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(if (compactAccessoryKeys) 32.dp else 38.dp),
                shape = RoundedCornerShape(9.dp),
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyboard_symbols),
                        contentDescription = uiText("編集キー"),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            KeyDivider()
            AccessoryKey("↶", uiText("元に戻す"), command = true, enabled = undoAvailable) {
                onUndo()
            }
            AccessoryKey("↷", uiText("やり直す"), command = true, enabled = redoAvailable) {
                onRedo()
            }
            AccessoryKey("⌕", uiText("検索と置換"), command = true) {
                onSearch()
            }
            AccessoryKey("{…}", uiText("スニペット"), command = true) { onSnippets() }
            AccessoryKey("?", uiText("p5.jsリファレンス"), command = true) { onReference() }
            AccessoryKey("≡", uiText("コードを整形"), command = true) {
                onFormat()
            }

            val detectedColorTarget = remember(editingValue.text, editingValue.selection) {
                findColorAtSelection(editingValue.text, editingValue.selection)
            }

            Surface(
                onClick = {
                    onOpenColorPicker(
                        detectedColorTarget,
                        detectedColorTarget?.color ?: lastColorPickerColor
                    )
                },
                modifier = Modifier
                    .width(
                        (if (compactAccessoryKeys) {
                            (40 - 6).coerceAtLeast(34)
                        } else {
                            40
                        }).dp
                    )
                    .height(if (compactAccessoryKeys) 32.dp else 38.dp)
                    .semantics {
                        contentDescription = uiText(if (detectedColorTarget != null) "カラーを編集" else "カラーピッカー")
                    },
                shape = RoundedCornerShape(8.dp),
                color = detectedColorTarget?.color ?: colors.surface,
                contentColor = if (detectedColorTarget != null) {
                    if (detectedColorTarget.color.luminance() > 0.5f) Color.Black else Color.White
                } else colors.onSurface,
                border = BorderStroke(
                    1.dp,
                    if (detectedColorTarget != null) Color.White.copy(alpha = 0.85f) else colors.outlineVariant
                ),
                shadowElevation = 1.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (detectedColorTarget == null) {
                        Text(
                            text = "🎨",
                            fontSize = if (compactAccessoryKeys) 12.sp else 14.sp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(if (compactAccessoryKeys) 14.dp else 16.dp)
                                .clip(CircleShape)
                                .border(
                                    1.dp,
                                    if (detectedColorTarget.color.luminance() > 0.5f) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.75f),
                                    CircleShape
                                )
                        )
                    }
                }
            }

            if (showAccessoryNavigation) {
                KeyDivider()
                AccessoryKey("TAB", uiText("インデント"), 54, true) {
                    onApplyEdit(changeLineIndent(editingValue, true))
                }
                AccessoryKey("⇤", uiText("インデントを戻す"), command = true) {
                    onApplyEdit(changeLineIndent(editingValue, false))
                }
                AccessoryKey("←", uiText("左へ移動"), onClick = moveLeft)
                AccessoryKey("↑", uiText("上へ移動")) {
                    onApplyEdit(moveCursorVertically(editingValue, -1))
                }
                AccessoryKey("↓", uiText("下へ移動")) {
                    onApplyEdit(moveCursorVertically(editingValue, 1))
                }
                AccessoryKey("→", uiText("右へ移動"), onClick = moveRight)
            }

            if (showAccessorySymbols) {
                KeyDivider()
                listOf(
                    Triple("{}", "{", "}"), Triple("()", "(", ")"),
                    Triple("[]", "[", "]"), Triple("\"\"", "\"", "\""),
                    Triple("''", "'", "'")
                ).forEach { (label, opening, closing) ->
                    AccessoryKey(label, uiText("%s を入力", label)) {
                        onApplyEdit(insertAtSelection(editingValue, opening, closing))
                    }
                }
                listOf(";", "=", ",", ".").forEach { symbol ->
                    AccessoryKey(symbol, uiText("%s を入力", symbol)) {
                        onApplyEdit(insertAtSelection(editingValue, symbol))
                    }
                }
                AccessoryKey("//", uiText("コメントを入力"), 44) {
                    onApplyEdit(insertAtSelection(editingValue, "// "))
                }
            }
        }
    }
}

@Composable
internal fun CompletionBar(
    suggestions: List<CompletionCandidate>,
    editingValue: TextFieldValue,
    editingText: String,
    visible: Boolean,
    codeFontFamily: FontFamily,
    colors: ColorScheme,
    textTranslator: (String, Array<out Any?>) -> String,
    onApplyCompletion: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && suggestions.isNotEmpty(),
        modifier = modifier,
        enter = fadeIn(tween(100)) + expandVertically(tween(130)),
        exit = fadeOut(tween(80)) + shrinkVertically(tween(110))
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.surfaceContainer,
            border = BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                suggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            val end = editingValue.selection.start
                            val start = (end - completionPrefix(editingValue).length).coerceAtLeast(0)
                            onApplyCompletion(
                                TextFieldValue(
                                    editingText.replaceRange(start, end, suggestion.name),
                                    TextRange(start + suggestion.name.length)
                                )
                            )
                        },
                        label = {
                            Text(
                                suggestion.file?.let { "${suggestion.name} · $it" }
                                    ?: completionHelp(suggestion.name) { s -> textTranslator(s, emptyArray()) },
                                fontFamily = codeFontFamily,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }
        }
    }
}
