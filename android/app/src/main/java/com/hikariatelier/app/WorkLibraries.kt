package com.hikariatelier.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal val bundledWorkLibraries = linkedMapOf(
    "p5.brush" to "2.2.1",
    "matter-js" to "0.20.0"
)

internal fun normalizedWorkLibraries(libraries: Map<String, String>): Map<String, String> =
    bundledWorkLibraries.filter { (name, version) -> libraries[name] == version }

@Composable
internal fun WorkLibraryControls(
    libraries: Map<String, String>,
    p5Version: String,
    onChange: (Map<String, String>) -> Unit,
    text: (String) -> String
) {
    Text(text("作品のライブラリ"), style = MaterialTheme.typography.titleSmall)
    bundledWorkLibraries.forEach { (name, version) ->
        val available = name != "p5.brush" || p5Version == P5_VERSION_CURRENT
        val description = when (name) {
            "p5.brush" -> text("水彩・鉛筆の表現（p5.js 2.3.3 / WEBGL）")
            "matter-js" -> text("2D物理演算エンジン（剛体・重力・衝突）")
            else -> ""
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$name $version")
                if (description.isNotEmpty()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = libraries[name] == version,
                enabled = available,
                onCheckedChange = { checked ->
                    onChange(if (checked) libraries + (name to version) else libraries - name)
                }
            )
        }
    }
    Text(
        text("追加ライブラリの読み込み順") + ": " +
            (bundledWorkLibraries.keys.filter { it in libraries } + "sketch.js").joinToString(" → "),
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.labelSmall
    )
}
