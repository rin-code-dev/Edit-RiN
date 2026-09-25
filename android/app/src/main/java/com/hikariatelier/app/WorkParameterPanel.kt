package com.hikariatelier.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

internal fun formatNumberValue(v: Float, step: Float): String {
    val stepStr = step.toString()
    val decimals = if (stepStr.contains('.')) {
        val frac = stepStr.substringAfter('.').trimEnd('0')
        frac.length.coerceIn(0, 4)
    } else 0
    return if (decimals == 0) {
        v.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.${decimals}f", v)
    }
}

@Composable
internal fun WorkParameterPanel(
    parameters: List<WorkParameter>,
    values: Map<String, String>,
    onChange: (WorkParameter, String) -> Unit,
    onCommit: () -> Unit,
    text: (String) -> String,
    onResetParameter: ((WorkParameter) -> Unit)? = null,
    onResetAll: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val hasModified = remember(parameters, values) {
        parameters.any { p ->
            val cur = parameterValue(p, values[p.name])
            when (p) {
                is WorkParameter.Color -> !cur.equals(p.defaultValue, ignoreCase = true)
                is WorkParameter.Boolean -> !cur.equals(p.defaultValue, ignoreCase = true)
                is WorkParameter.Number -> {
                    val curF = cur.toFloatOrNull() ?: 0f
                    val defF = p.defaultValue.toFloatOrNull() ?: 0f
                    Math.abs(curF - defF) > 0.0001f
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text("パラメータ"), style = MaterialTheme.typography.titleLarge)
                if (parameters.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${parameters.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (hasModified && onResetAll != null) {
                    TextButton(
                        onClick = onResetAll,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_restore),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text("初期値に戻す"), style = MaterialTheme.typography.labelMedium)
                    }
                }
                onDismiss?.let { dismiss ->
                    IconButton(onClick = dismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = text("全画面表示を閉じる")
                        )
                    }
                }
            }
        }

        // Empty state
        if (parameters.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text("コードにパラメータ宣言を追加してください"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text("コードでは rinParams.speed と rinParams.ink を使います"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        Text(
                            text = "// @rin number speed \"Speed\" 0 3 1 0.1\n// @rin color ink \"Color\" #BA90E2\n// @rin boolean glow \"Glow\" true",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val template = "// @rin number speed \"Speed\" 0 3 1 0.1\n// @rin color ink \"Color\" #BA90E2\n// @rin boolean glow \"Glow\" true\n"
                            clipboard.setPrimaryClip(ClipData.newPlainText("rinParams", template))
                            Toast.makeText(context, text("テンプレートをコピーしました"), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_snippet),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text("テンプレートをコピー"))
                    }
                }
            }
        }

        // Parameter items
        parameters.forEach { parameter ->
            val value = parameterValue(parameter, values[parameter.name])
            val isModified = when (parameter) {
                is WorkParameter.Color -> !value.equals(parameter.defaultValue, ignoreCase = true)
                is WorkParameter.Boolean -> !value.equals(parameter.defaultValue, ignoreCase = true)
                is WorkParameter.Number -> {
                    val curF = value.toFloatOrNull() ?: 0f
                    val defF = parameter.defaultValue.toFloatOrNull() ?: 0f
                    Math.abs(curF - defF) > 0.0001f
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (parameter) {
                        is WorkParameter.Number -> {
                            val numeric = value.toFloatOrNull() ?: parameter.defaultValue.toFloatOrNull() ?: 0f
                            val formattedVal = formatNumberValue(numeric, parameter.step)

                            // Item Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = parameter.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "rinParams.${parameter.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = formattedVal,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    if (isModified && onResetParameter != null) {
                                        IconButton(
                                            onClick = { onResetParameter(parameter) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_restore),
                                                contentDescription = text("初期値に戻す"),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Stepper & Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val nextVal = (numeric - parameter.step).coerceIn(parameter.min, parameter.max)
                                        val nextStr = formatNumberValue(nextVal, parameter.step)
                                        onChange(parameter, nextStr)
                                        onCommit()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 32.dp),
                                    enabled = numeric > parameter.min
                                ) {
                                    Text("−", fontWeight = FontWeight.Bold)
                                }

                                Slider(
                                    value = numeric,
                                    onValueChange = { raw ->
                                        val steps = ((raw - parameter.min) / parameter.step).roundToInt()
                                        val snapped = (parameter.min + steps * parameter.step)
                                            .coerceIn(parameter.min, parameter.max)
                                        onChange(parameter, formatNumberValue(snapped, parameter.step))
                                    },
                                    onValueChangeFinished = onCommit,
                                    valueRange = parameter.min..parameter.max,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedButton(
                                    onClick = {
                                        val nextVal = (numeric + parameter.step).coerceIn(parameter.min, parameter.max)
                                        val nextStr = formatNumberValue(nextVal, parameter.step)
                                        onChange(parameter, nextStr)
                                        onCommit()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 32.dp),
                                    enabled = numeric < parameter.max
                                ) {
                                    Text("+", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Range hint footer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${formatNumberValue(parameter.min, parameter.step)} .. ${formatNumberValue(parameter.max, parameter.step)} (step: ${formatNumberValue(parameter.step, parameter.step)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isModified) {
                                    Text(
                                        text = "${text("初期値")}: ${parameter.defaultValue}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        is WorkParameter.Boolean -> {
                            val boolVal = value.toBoolean()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = parameter.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "rinParams.${parameter.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (boolVal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = if (boolVal) "ON" else "OFF",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (boolVal) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    Switch(
                                        checked = boolVal,
                                        onCheckedChange = { checked ->
                                            onChange(parameter, checked.toString())
                                            onCommit()
                                        }
                                    )

                                    if (isModified && onResetParameter != null) {
                                        IconButton(
                                            onClick = { onResetParameter(parameter) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_restore),
                                                contentDescription = text("初期値に戻す"),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is WorkParameter.Color -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = parameter.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "rinParams.${parameter.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isModified && onResetParameter != null) {
                                    IconButton(
                                        onClick = { onResetParameter(parameter) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_restore),
                                            contentDescription = text("初期値に戻す"),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            CustomColorSetting(
                                label = "",
                                value = (value.removePrefix("#").toIntOrNull(16) ?: 0xFFFFFF) or 0xFF000000.toInt(),
                                onChange = { color ->
                                    onChange(parameter, "#%06X".format(color and 0xFFFFFF))
                                    onCommit()
                                },
                                text = text
                            )
                        }
                    }
                }
            }
        }
    }
}
