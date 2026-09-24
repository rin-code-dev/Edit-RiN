package com.hikariatelier.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun WorkParameterPanel(
    parameters: List<WorkParameter>,
    values: Map<String, String>,
    onChange: (WorkParameter, String) -> Unit,
    onCommit: () -> Unit,
    text: (String) -> String
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text("パラメータ"), style = MaterialTheme.typography.titleMedium)
        if (parameters.isEmpty()) {
            Text(text("コードにパラメータ宣言を追加してください"))
            Text("// @rin number speed \"Speed\" 0 3 1 0.1\n// @rin color ink \"Color\" #BA90E2",
                style = MaterialTheme.typography.bodySmall)
            Text(text("コードでは rinParams.speed と rinParams.ink を使います"),
                style = MaterialTheme.typography.bodySmall)
        }
        parameters.forEach { parameter ->
            val value = parameterValue(parameter, values[parameter.name])
            when (parameter) {
                is WorkParameter.Number -> {
                    val numeric = value.toFloat()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(parameter.label)
                        Text(value, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = numeric,
                        onValueChange = { raw ->
                            val steps = ((raw - parameter.min) / parameter.step).roundToInt()
                            val snapped = (parameter.min + steps * parameter.step)
                                .coerceIn(parameter.min, parameter.max)
                            onChange(parameter, snapped.toString())
                        },
                        onValueChangeFinished = onCommit,
                        valueRange = parameter.min..parameter.max
                    )
                }
                is WorkParameter.Color -> CustomColorSetting(
                    label = parameter.label,
                    value = (value.removePrefix("#").toInt(16) or 0xFF000000.toInt()),
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
