package com.hikariatelier.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
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
    text: (String) -> String,
    enabled: Boolean = true
) {
    Text(text("作品のライブラリ"), style = MaterialTheme.typography.titleSmall)
    bundledWorkLibraries.forEach { (name, version) ->
        val available = name != "p5.brush" || normalizedP5Version(p5Version) == P5_VERSION_CURRENT
        val description = when (name) {
            "p5.brush" -> text("水彩・鉛筆の表現（p5.js 2.3.4 / WEBGL）")
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
                enabled = available && enabled,
                onCheckedChange = { checked ->
                    onChange(if (checked) libraries + (name to version) else libraries - name)
                }
            )
        }
    }
    Text(
        text("内蔵ライブラリの読み込み順") + ": " +
            bundledWorkLibraries.keys.filter { it in libraries }.joinToString(" → ").ifEmpty { text("なし") },
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.labelSmall
    )
}

@Composable
internal fun ProjectDocumentControls(
    files: Map<String, String>,
    resolvedRun: ResolvedProjectRun?,
    config: ProjectDocumentConfig,
    enabled: Boolean,
    onChange: (ProjectDocumentConfig) -> Unit,
    text: (String) -> String
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(text("プロジェクトの読み込み設定"), modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_chevron_down), null,
            Modifier.size(18.dp).rotate(if (expanded) 180f else 0f))
    }
    if (!expanded) return
    val htmlFiles = files.keys.filter { isHtmlProjectFile(it) }.sorted()
    val moduleFiles = (files.keys.filter { isJavaScriptProjectFile(it) } + "sketch.js").distinct().sorted()
    val htmlMode = config.executionMode == "html" || resolvedRun?.documentMode == true
    // Invalid explicit entries still expose their controls so the user can repair them.
    val moduleMode = !htmlMode && (resolvedRun?.moduleMode ?:
        (config.executionMode == "module" || config.executionMode != "classic" && config.moduleEntry != null))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("auto" to "自動", "classic" to "通常JavaScript", "module" to "モジュール", "html" to "HTMLページ").forEach { (mode, label) ->
                FilterChip(selected = config.executionMode == mode, enabled = enabled,
                    onClick = { onChange(config.copy(executionMode = mode,
                        entryDocument = if (mode == "html") config.entryDocument?.takeIf { it in htmlFiles }
                            ?: htmlFiles.firstOrNull { it.substringAfterLast('/').equals("index.html", true) }
                            ?: htmlFiles.firstOrNull() else null,
                        moduleEntry = if (mode == "module") config.moduleEntry?.takeIf { it in moduleFiles }
                            ?: moduleFiles.filter { it.endsWith(".mjs", true) }.singleOrNull() ?: "sketch.js" else null
                    )) }, label = { Text(text(label)) })
            }
        }
        when (config.executionMode) {
            "html" -> ProjectEntrySelector(text("開始するHTMLファイル"),
                config.entryDocument ?: htmlFiles.firstOrNull { it == "index.html" } ?: htmlFiles.firstOrNull(),
                htmlFiles, enabled, text) { onChange(config.copy(entryDocument = it)) }
            "module" -> ProjectEntrySelector(text("開始するモジュール"), config.moduleEntry ?: "sketch.js",
                moduleFiles, enabled, text) { onChange(config.copy(moduleEntry = it)) }
            "auto" -> Text(text("index.html があれば優先します。メインコードに import/export がある場合、またはメインコードと通常JSが空で .mjs ファイルが1つだけある場合は、モジュールとして実行します。"),
                style = MaterialTheme.typography.bodySmall)
        }
        if (htmlMode) {
            Text(text("HTML内の script タグでライブラリ・読み込み順・モジュールを指定してください。"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            if (!moduleMode) {
                Text(text("JavaScriptの読み込み順"), style = MaterialTheme.typography.titleSmall)
                val order = orderedClassicScriptFiles(files, config)
                order.forEachIndexed { index, name ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        ProjectOrderButtons(index, order.size, enabled, text) { destination ->
                            val updated = order.toMutableList().apply { add(destination, removeAt(index)) }
                            onChange(config.copy(classicScriptOrder = updated))
                        }
                    }
                }
                TextButton(enabled = enabled && config.classicScriptOrder.isNotEmpty(),
                    onClick = { onChange(config.copy(classicScriptOrder = emptyList())) }) {
                    Text(text("名前順に戻す"))
                }
            }
            HorizontalDivider()
            Text(text("外部ライブラリ（HTTPS）"), style = MaterialTheme.typography.titleSmall)
            Text(text("通常JSは、登録した順に作品コードより先に実行します。モジュールはブラウザーの仕組みで読み込みます。"),
                style = MaterialTheme.typography.bodySmall)
            config.libraries.forEachIndexed { index, library ->
                Column {
                    Text(library.url, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = library.type == "module", enabled = enabled,
                            onClick = {
                                val libraries = config.libraries.toMutableList()
                                libraries[index] = library.copy(type = if (library.type == "module") "classic" else "module")
                                onChange(config.copy(libraries = libraries))
                            }, label = { Text(if (library.type == "module") text("モジュール") else text("通常JavaScript")) })
                        Spacer(Modifier.weight(1f))
                        ProjectOrderButtons(index, config.libraries.size, enabled, text) { destination ->
                            val libraries = config.libraries.toMutableList().apply { add(destination, removeAt(index)) }
                            onChange(config.copy(libraries = libraries))
                        }
                        IconButton(enabled = enabled, onClick = {
                            onChange(config.copy(libraries = config.libraries.filterIndexed { i, _ -> i != index }))
                        }) { Icon(painterResource(R.drawable.ic_delete), text("削除"), Modifier.size(18.dp)) }
                    }
                }
            }
            var libraryUrl by rememberSaveable { mutableStateOf("") }
            var libraryModule by rememberSaveable { mutableStateOf(false) }
            val normalizedUrl = normalizedExternalScriptUrl(libraryUrl)
            OutlinedTextField(value = libraryUrl, onValueChange = { libraryUrl = it }, enabled = enabled,
                label = { Text(text("ライブラリのHTTPS URL")) }, singleLine = true,
                isError = libraryUrl.isNotBlank() && normalizedUrl == null, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = libraryModule, enabled = enabled, onClick = { libraryModule = !libraryModule },
                    label = { Text(text("モジュールとして読み込む")) })
                Spacer(Modifier.weight(1f))
                TextButton(enabled = enabled && config.libraries.size < 50 && normalizedUrl != null && config.libraries.none { it.url == normalizedUrl },
                    onClick = {
                        normalizedUrl?.let { url ->
                            onChange(config.copy(libraries = config.libraries + ProjectLibraryScript(url, if (libraryModule) "module" else "classic")))
                            libraryUrl = ""
                        }
                    }) { Text(text("追加")) }
            }
            OutlinedTextField(value = config.p5Url.orEmpty(),
                onValueChange = { onChange(config.copy(p5Url = it.trim().takeIf(String::isNotEmpty))) },
                enabled = enabled, label = { Text(text("p5.jsのHTTPS URL（任意）")) }, singleLine = true,
                isError = config.p5Url != null && normalizedExternalScriptUrl(config.p5Url) == null,
                modifier = Modifier.fillMaxWidth())
            Text(text("空欄の場合は内蔵のp5.jsを使います。外部URLから読み込むには、インターネット接続が必要です。"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text("設定は edit-rin.json に保存されます。作品と一緒に別の環境へ移せます。"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProjectEntrySelector(
    label: String, value: String?, choices: List<String>, enabled: Boolean,
    text: (String) -> String, onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelLarge)
    Box {
        OutlinedButton(enabled = enabled && choices.isNotEmpty(), onClick = { expanded = true }) {
            Text(value ?: text("ファイルを追加してください"))
            Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onChange(name); expanded = false })
            }
        }
    }
}

@Composable
private fun ProjectOrderButtons(index: Int, size: Int, enabled: Boolean, text: (String) -> String, move: (Int) -> Unit) {
    IconButton(enabled = enabled && index > 0, onClick = { move(index - 1) }) {
        Icon(painterResource(R.drawable.ic_chevron_down), text("上へ"), Modifier.size(18.dp).rotate(180f))
    }
    IconButton(enabled = enabled && index < size - 1, onClick = { move(index + 1) }) {
        Icon(painterResource(R.drawable.ic_chevron_down), text("下へ"), Modifier.size(18.dp))
    }
}
