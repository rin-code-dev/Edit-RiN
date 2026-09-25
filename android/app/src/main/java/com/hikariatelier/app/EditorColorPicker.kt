package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

data class EditorColorTarget(
    val range: TextRange,
    val color: Color,
    val originalText: String,
    val isHex: Boolean,
    val quote: String = "",
    val functionName: String = "",
    val hasAlpha: Boolean = false
)

fun parseHexColor(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    return when (clean.length) {
        3 -> {
            val r = clean[0].digitToIntOrNull(16) ?: return null
            val g = clean[1].digitToIntOrNull(16) ?: return null
            val b = clean[2].digitToIntOrNull(16) ?: return null
            Color(r * 17, g * 17, b * 17)
        }
        4 -> {
            val r = clean[0].digitToIntOrNull(16) ?: return null
            val g = clean[1].digitToIntOrNull(16) ?: return null
            val b = clean[2].digitToIntOrNull(16) ?: return null
            val a = clean[3].digitToIntOrNull(16) ?: return null
            Color(r * 17, g * 17, b * 17, a * 17)
        }
        6 -> {
            val num = clean.toLongOrNull(16) ?: return null
            Color((0xFF000000L or num).toInt())
        }
        8 -> {
            val r = clean.substring(0, 2).toIntOrNull(16) ?: return null
            val g = clean.substring(2, 4).toIntOrNull(16) ?: return null
            val b = clean.substring(4, 6).toIntOrNull(16) ?: return null
            val a = clean.substring(6, 8).toIntOrNull(16) ?: return null
            Color(r, g, b, a)
        }
        else -> null
    }
}

fun formatHexColor(color: Color, includeAlpha: Boolean = false): String {
    val r = (color.red * 255).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255).roundToInt().coerceIn(0, 255)
    val a = (color.alpha * 255).roundToInt().coerceIn(0, 255)
    return if (includeAlpha && a != 255) {
        "#%02X%02X%02X%02X".format(r, g, b, a)
    } else {
        "#%02X%02X%02X".format(r, g, b)
    }
}

fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    val r = (color.red * 255).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255).roundToInt().coerceIn(0, 255)
    android.graphics.Color.RGBToHSV(r, g, b, hsv)
    return hsv
}

fun hsvToColor(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Color {
    val argb = android.graphics.Color.HSVToColor(
        (alpha * 255).roundToInt().coerceIn(0, 255),
        floatArrayOf(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    )
    return Color(argb)
}

fun findColorAtSelection(text: String, selection: TextRange): EditorColorTarget? {
    if (text.isEmpty()) return null
    val cursor = if (selection.collapsed) selection.start else selection.min
    if (cursor < 0 || cursor > text.length) return null

    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
    if (lineStart > lineEnd) return null
    val line = text.substring(lineStart, lineEnd)
    val offsetInLine = (cursor - lineStart).coerceIn(0, line.length)

    // 1. Quoted hex: e.g. '#ff0055', "#ff0055", `#ff0055`
    val quotedHexRegex = """(['"`])(#[0-9a-fA-F]{3,8})\1""".toRegex()
    for (match in quotedHexRegex.findAll(line)) {
        val start = match.range.first
        val end = match.range.last + 1
        if (offsetInLine in start..end) {
            val quote = match.groupValues[1]
            val hexStr = match.groupValues[2]
            val parsed = parseHexColor(hexStr)
            if (parsed != null) {
                return EditorColorTarget(
                    range = TextRange(lineStart + start, lineStart + end),
                    color = parsed,
                    originalText = match.value,
                    isHex = true,
                    quote = quote,
                    hasAlpha = hexStr.length in listOf(5, 9)
                )
            }
        }
    }

    // 2. Bare hex: e.g. #ff0055
    val bareHexRegex = """(?<![0-9a-zA-Z])(#[0-9a-fA-F]{3,8})\b""".toRegex()
    for (match in bareHexRegex.findAll(line)) {
        val start = match.range.first
        val end = match.range.last + 1
        if (offsetInLine in start..end) {
            val hexStr = match.groupValues[1]
            val parsed = parseHexColor(hexStr)
            if (parsed != null) {
                return EditorColorTarget(
                    range = TextRange(lineStart + start, lineStart + end),
                    color = parsed,
                    originalText = match.value,
                    isHex = true,
                    quote = "",
                    hasAlpha = hexStr.length in listOf(5, 9)
                )
            }
        }
    }

    // 3. Color functions: e.g. fill(255, 100, 50), color(0, 128, 255, 0.5)
    val funcRegex = """\b(color|fill|stroke|background|rgb|rgba)\s*\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*([0-9.]+))?\s*\)""".toRegex()
    for (match in funcRegex.findAll(line)) {
        val start = match.range.first
        val end = match.range.last + 1
        if (offsetInLine in start..end) {
            val r = match.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: 0
            val g = match.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: 0
            val b = match.groupValues[4].toIntOrNull()?.coerceIn(0, 255) ?: 0
            val aStr = match.groupValues.getOrNull(5)
            val a = if (!aStr.isNullOrBlank()) {
                val aVal = aStr.toFloatOrNull() ?: 1f
                if (aVal <= 1f) (aVal * 255).roundToInt().coerceIn(0, 255) else aVal.roundToInt().coerceIn(0, 255)
            } else 255
            return EditorColorTarget(
                range = TextRange(lineStart + start, lineStart + end),
                color = Color(r, g, b, a),
                originalText = match.value,
                isHex = false,
                functionName = match.groupValues[1],
                hasAlpha = !aStr.isNullOrBlank()
            )
        }
    }

    return null
}

fun replaceColorTarget(value: TextFieldValue, target: EditorColorTarget, newColor: Color): TextFieldValue {
    val replacement = if (target.isHex) {
        val hex = formatHexColor(newColor, target.hasAlpha)
        if (target.quote.isNotEmpty()) "${target.quote}$hex${target.quote}" else hex
    } else {
        val r = (newColor.red * 255).roundToInt().coerceIn(0, 255)
        val g = (newColor.green * 255).roundToInt().coerceIn(0, 255)
        val b = (newColor.blue * 255).roundToInt().coerceIn(0, 255)
        if (target.hasAlpha) {
            val a = ((newColor.alpha * 100).roundToInt() / 100f)
            "${target.functionName}($r, $g, $b, $a)"
        } else {
            "${target.functionName}($r, $g, $b)"
        }
    }
    val newText = value.text.replaceRange(target.range.start, target.range.end, replacement)
    val newCursor = target.range.start + replacement.length
    return TextFieldValue(newText, TextRange(newCursor))
}

fun insertColorAtCursor(value: TextFieldValue, newColor: Color): TextFieldValue {
    val hex = "\"${formatHexColor(newColor)}\""
    val start = value.selection.min
    val end = value.selection.max
    val newText = value.text.replaceRange(start, end, hex)
    val newCursor = start + hex.length
    return TextFieldValue(newText, TextRange(newCursor))
}

private val PRESET_COLORS = listOf(
    Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
    Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF00BCD4), Color(0xFF009688),
    Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFFFEB3B), Color(0xFFFF9800),
    Color(0xFFFF5722), Color(0xFF795548), Color(0xFFFFFFFF), Color(0xFF9E9E9E),
    Color(0xFF37474F), Color(0xFF000000)
)

@Composable
fun EditorColorPickerDialog(
    initialColor: Color,
    target: EditorColorTarget?,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit,
    text: (String) -> String
) {
    val initialHsv = remember(initialColor) { colorToHsv(initialColor) }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }
    var alpha by remember { mutableStateOf(initialColor.alpha) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = HSV, 1 = RGB

    val currentColor = remember(hue, saturation, value, alpha) {
        hsvToColor(hue, saturation, value, alpha)
    }

    var hexInput by remember { mutableStateOf(formatHexColor(currentColor, target?.hasAlpha == true).removePrefix("#")) }

    fun updateFromColor(c: Color) {
        val hsv = colorToHsv(c)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        alpha = c.alpha
        hexInput = formatHexColor(c, target?.hasAlpha == true).removePrefix("#")
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (target != null) text("カラーを編集") else text("カラーを挿入"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss) {
                        Text(text("キャンセル"), style = MaterialTheme.typography.labelLarge)
                    }
                }

                // Preview & Hex Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Preview Swatch
                    Box(
                        modifier = Modifier
                            .size(width = 68.dp, height = 52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    ) {
                        if (target != null) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(target.color)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(currentColor)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(currentColor)
                            )
                        }
                    }

                    // Hex Input
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            val clean = raw.removePrefix("#")
                            if (clean.length <= 8 && clean.all { it in "0123456789abcdefABCDEF" }) {
                                hexInput = clean.uppercase()
                                val parsed = parseHexColor("#$clean")
                                if (parsed != null) {
                                    val hsv = colorToHsv(parsed)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    value = hsv[2]
                                    alpha = parsed.alpha
                                }
                            }
                        },
                        prefix = { Text("#", fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        label = { Text(text("カラーコード")) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Mode Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("HSV", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("RGB", fontWeight = FontWeight.SemiBold) }
                    )
                }

                if (selectedTab == 0) {
                    // HSV Mode
                    // Hue Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text("色相"), style = MaterialTheme.typography.labelMedium)
                            Text("${hue.roundToInt()}°", style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = hue,
                            onValueChange = {
                                hue = it
                                hexInput = formatHexColor(hsvToColor(hue, saturation, value, alpha), target?.hasAlpha == true).removePrefix("#")
                            },
                            valueRange = 0f..360f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Saturation Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text("彩度"), style = MaterialTheme.typography.labelMedium)
                            Text("${(saturation * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = saturation,
                            onValueChange = {
                                saturation = it
                                hexInput = formatHexColor(hsvToColor(hue, saturation, value, alpha), target?.hasAlpha == true).removePrefix("#")
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Value/Brightness Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text("明度"), style = MaterialTheme.typography.labelMedium)
                            Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = value,
                            onValueChange = {
                                value = it
                                hexInput = formatHexColor(hsvToColor(hue, saturation, value, alpha), target?.hasAlpha == true).removePrefix("#")
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    // RGB Mode
                    val currentR = (currentColor.red * 255).roundToInt().coerceIn(0, 255)
                    val currentG = (currentColor.green * 255).roundToInt().coerceIn(0, 255)
                    val currentB = (currentColor.blue * 255).roundToInt().coerceIn(0, 255)

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("R (赤)", style = MaterialTheme.typography.labelMedium)
                            Text("$currentR", style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = currentR.toFloat(),
                            onValueChange = {
                                updateFromColor(Color(it.roundToInt(), currentG, currentB, (alpha * 255).roundToInt()))
                            },
                            valueRange = 0f..255f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("G (緑)", style = MaterialTheme.typography.labelMedium)
                            Text("$currentG", style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = currentG.toFloat(),
                            onValueChange = {
                                updateFromColor(Color(currentR, it.roundToInt(), currentB, (alpha * 255).roundToInt()))
                            },
                            valueRange = 0f..255f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("B (青)", style = MaterialTheme.typography.labelMedium)
                            Text("$currentB", style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = currentB.toFloat(),
                            onValueChange = {
                                updateFromColor(Color(currentR, currentG, it.roundToInt(), (alpha * 255).roundToInt()))
                            },
                            valueRange = 0f..255f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Opacity Slider (if alpha is supported/used)
                if (target?.hasAlpha == true || alpha < 1f) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text("不透明度"), style = MaterialTheme.typography.labelMedium)
                            Text("${(alpha * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = alpha,
                            onValueChange = {
                                alpha = it
                                hexInput = formatHexColor(hsvToColor(hue, saturation, value, alpha), true).removePrefix("#")
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text("プリセット"), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PRESET_COLORS.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(preset)
                                    .border(
                                        1.dp,
                                        if (preset.luminance() > 0.6f) MaterialTheme.colorScheme.outline else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { updateFromColor(preset) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Apply button
                Button(
                    onClick = {
                        onApply(currentColor)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (target != null) text("適用") else text("挿入"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
