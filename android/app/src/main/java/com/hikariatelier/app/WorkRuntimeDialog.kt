package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    initialFiles: Map<String, String>,
    initialSource: String,
    busy: Boolean,
    uiText: (String) -> String,
    onSave: (version: String, soundEnabled: Boolean, libraries: Map<String, String>, config: ProjectDocumentConfig) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val colors = MaterialTheme.colorScheme
    var runtimeP5Version by rememberSaveable(visible, initialP5Version) { mutableStateOf(initialP5Version) }
    var runtimeSoundEnabled by rememberSaveable(visible, initialSoundEnabled) { mutableStateOf(initialSoundEnabled) }
    var runtimeLibraries by rememberSaveable(visible, initialLibraries) { mutableStateOf(initialLibraries.toMap()) }
    val initialConfig = remember(initialFiles) { runCatching { readProjectDocumentConfig(initialFiles) } }
    var projectConfig by remember(visible, initialFiles) { mutableStateOf(initialConfig.getOrDefault(ProjectDocumentConfig())) }
    val resolvedRun = remember(initialFiles, projectConfig, initialSource) {
        runCatching { resolveProjectRun(initialFiles, projectConfig, initialSource) }
    }
    val configValid = initialConfig.isSuccess &&
        (projectConfig.p5Url == null || normalizedExternalScriptUrl(projectConfig.p5Url.orEmpty()) != null) &&
        resolvedRun.isSuccess
    val usesHtml = resolvedRun.getOrNull()?.documentMode == true ||
        projectConfig.executionMode == "html"

    EditSettingsDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_code), contentDescription = null) },
        title = { Text(uiText("実行環境")) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (usesHtml) Text(uiText("HTML作品では、p5.jsとライブラリをHTML内のscriptタグで指定します。"),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
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
                        enabled = !busy && !usesHtml,
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
                        enabled = !busy && !usesHtml,
                        checked = runtimeSoundEnabled,
                        onCheckedChange = { runtimeSoundEnabled = it }
                    )
                }
                WorkLibraryControls(
                    libraries = runtimeLibraries,
                    p5Version = runtimeP5Version,
                    onChange = { runtimeLibraries = it },
                    text = { uiText(it) },
                    enabled = !busy && !usesHtml
                )
                HorizontalDivider()
                if (initialConfig.isFailure) {
                    Text(uiText("edit-rin.json の設定を読み取れません。プロジェクトファイルから修正してください。"),
                        color = colors.error, style = MaterialTheme.typography.bodySmall)
                } else {
                    ProjectDocumentControls(files = initialFiles, resolvedRun = resolvedRun.getOrNull(), config = projectConfig,
                        enabled = !busy, onChange = { projectConfig = it }, text = uiText)
                    if (!configValid) Text(uiText("開始するファイルまたはHTTPS URLを確認してください。"),
                        color = colors.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && configValid, onClick = {
                onSave(runtimeP5Version, runtimeSoundEnabled, runtimeLibraries, projectConfig)
            }) { Text(uiText("保存")) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(uiText("キャンセル")) }
        }
    )
}
