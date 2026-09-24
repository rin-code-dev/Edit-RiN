package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun WorkRuntimeDialog(
    visible: Boolean,
    initialP5Version: String,
    initialSoundEnabled: Boolean,
    initialLibraries: Map<String, String>,
    uiText: (String) -> String,
    onSave: (version: String, soundEnabled: Boolean, libraries: Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val colors = MaterialTheme.colorScheme
    var runtimeP5Version by remember(visible, initialP5Version) { mutableStateOf(initialP5Version) }
    var runtimeSoundEnabled by remember(visible, initialSoundEnabled) { mutableStateOf(initialSoundEnabled) }
    var runtimeLibraries by remember(visible, initialLibraries) { mutableStateOf(initialLibraries) }

    EditSettingsDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_code), contentDescription = null) },
        title = { Text(uiText("実行環境")) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    uiText("p5.jsバージョン"),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface
                )
                listOf(
                    P5_VERSION_CURRENT to uiText("現在の標準"),
                    P5_VERSION_LEGACY to uiText("旧作品向け")
                ).forEach { (version, description) ->
                    val selected = runtimeP5Version == version
                    Surface(
                        onClick = {
                            runtimeP5Version = version
                            if (version != P5_VERSION_CURRENT) {
                                runtimeLibraries = runtimeLibraries - "p5.brush"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) colors.primary else colors.outlineVariant
                        ),
                        color = if (selected) colors.primaryContainer else colors.surface,
                        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "p5.js $version",
                                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                description,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "p5.sound",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurface
                        )
                        Text(
                            uiText("音声再生・合成・解析を有効にします"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = runtimeSoundEnabled,
                        onCheckedChange = { runtimeSoundEnabled = it }
                    )
                }
                WorkLibraryControls(
                    libraries = runtimeLibraries,
                    p5Version = runtimeP5Version,
                    onChange = { runtimeLibraries = it },
                    text = { uiText(it) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(runtimeP5Version, runtimeSoundEnabled, runtimeLibraries)
            }) { Text(uiText("保存")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(uiText("キャンセル")) }
        }
    )
}
