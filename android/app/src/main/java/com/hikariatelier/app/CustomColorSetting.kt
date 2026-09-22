package com.hikariatelier.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun CustomColorSetting(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    text: (String) -> String
) {
    fun hex(color: Int) = "%06X".format(color and 0xFFFFFF)
    var input by rememberSaveable { mutableStateOf(hex(value)) }
    LaunchedEffect(value) {
        if (input.toIntOrNull(16) != (value and 0xFFFFFF)) input = hex(value)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 8.dp).size(48.dp)
                .background(Color(value), RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)))
            OutlinedTextField(
                value = input,
                onValueChange = { raw ->
                    val digits = raw.removePrefix("#")
                    if (digits.length <= 6 && digits.all { it in "0123456789abcdefABCDEF" }) {
                        input = digits.uppercase()
                        if (digits.length == 6) onChange(digits.toInt(16) or 0xFF000000.toInt())
                    }
                },
                prefix = { Text("#") }, singleLine = true,
                label = { Text(text("6桁のカラーコード")) },
                isError = input.length != 6,
                modifier = Modifier.weight(1f)
            )
        }
        listOf("R" to 16, "G" to 8, "B" to 0).forEach { (channel, shift) ->
            val component = (value ushr shift) and 255
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$channel $component", modifier = Modifier.width(52.dp))
                Slider(value = component.toFloat(), valueRange = 0f..255f,
                    onValueChange = {
                        val updated = (value and (255 shl shift).inv()) or (it.roundToInt() shl shift)
                        input = hex(updated)
                        onChange(updated)
                    }, modifier = Modifier.weight(1f))
            }
        }
    }
}
