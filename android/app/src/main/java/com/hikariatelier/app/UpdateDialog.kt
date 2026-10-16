package com.hikariatelier.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.ConfigurationCompat
import java.util.Locale

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return if (digitGroups == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}

internal data class DisplayHighlight(val iconRes: Int, val title: String, val desc: String)

internal fun resolveHighlightIcon(title: String, desc: String): Int {
    val text = "$title $desc".lowercase(Locale.ROOT)
    return when {
        text.contains("param") || text.contains("slider") || text.contains("パラメータ") || text.contains("スライダー") -> R.drawable.ic_tune
        text.contains("tweet") || text.contains("つぶやき") || text.contains("golf") || text.contains("短縮") || text.contains("圧縮") || text.contains("share") || text.contains("共有") -> R.drawable.ic_share_card
        text.contains("pip") || text.contains("window") || text.contains("小窓") || text.contains("浮遊") || text.contains("ゴースト") -> R.drawable.ic_pip_opacity
        text.contains("sound") || text.contains("audio") || text.contains("音声") || text.contains("音") -> R.drawable.ic_play
        text.contains("asset") || text.contains("image") || text.contains("素材") || text.contains("画像") -> R.drawable.ic_assets
        text.contains("tour") || text.contains("guide") || text.contains("ツアー") || text.contains("ガイド") || text.contains("入門") -> R.drawable.ic_help
        text.contains("history") || text.contains("undo") || text.contains("履歴") -> R.drawable.ic_history
        text.contains("watch") || text.contains("バッジ") || text.contains("診断") || text.contains("変数") -> R.drawable.ic_quick_fix
        else -> R.drawable.ic_code
    }
}

@Composable
internal fun UpdateDialog(
    state: UpdateViewModel,
    text: (String) -> String,
    install: () -> Unit,
    openRelease: () -> Unit
) {
    val message = state.message ?: return
    val release = state.availableRelease
    val canDownload = release?.apk != null && (!BuildConfig.DEBUG || state.isTestPreview)
    val colors = MaterialTheme.colorScheme

    val configuration = LocalConfiguration.current
    val deviceLanguage = ConfigurationCompat.getLocales(configuration)[0]?.language ?: "en"
    val isJa = deviceLanguage.startsWith("ja")

    // Parse highlights for the user's language from release body if available;
    // otherwise fallback to pre-parsed highlights or embedded release notes in APP_RELEASE_NOTES.
    val dynamicParsed = if (release != null && release.body.isNotBlank()) {
        parseReleaseHighlights(release.body, deviceLanguage)
    } else emptyList()

    val rawHighlights = when {
        dynamicParsed.isNotEmpty() -> dynamicParsed
        release != null && release.highlights.isNotEmpty() -> release.highlights
        else -> emptyList()
    }

    val highlights: List<DisplayHighlight> = if (rawHighlights.isNotEmpty()) {
        rawHighlights.take(3).map { hl ->
            DisplayHighlight(
                iconRes = resolveHighlightIcon(hl.title, hl.description),
                title = hl.title,
                desc = hl.description
            )
        }
    } else {
        val note = APP_RELEASE_NOTES.firstOrNull {
            val relTag = release?.tag?.removePrefix("v") ?: ""
            it.versionName == relTag || it.versionName == release?.tag
        } ?: APP_RELEASE_NOTES.firstOrNull()
        note?.features?.take(3)?.map { feat ->
            DisplayHighlight(
                iconRes = feat.iconRes,
                title = if (isJa) feat.titleJa else feat.titleEn,
                desc = if (isJa) feat.descJa else feat.descEn
            )
        }.orEmpty()
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = (screenHeight * 0.86f).coerceAtLeast(360.dp)

    Dialog(
        onDismissRequest = { if (!state.downloading) state.dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = !state.downloading
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainer,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // 1. Header (Icon, Title, Chips, and Close Button)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (!state.downloading) {
                            IconButton(
                                onClick = { state.dismiss() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = text("閉じる"),
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_system_update),
                                    contentDescription = null,
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Text(
                                text = text("新しいバージョンがあります"),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val tagText = release?.tag ?: "v${BuildConfig.VERSION_NAME}"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.secondaryContainer)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = tagText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSecondaryContainer
                                    )
                                }

                                val apkSize = release?.apk?.size ?: 0L
                                if (apkSize > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.secondaryContainer)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = formatFileSize(apkSize),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Scrollable Body: Features & Download Progress
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (highlights.isNotEmpty() && release != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = text("主な更新内容"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 2.dp)
                                )

                                highlights.forEach { feature ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.surfaceContainerHigh)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(colors.primary.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(feature.iconRes),
                                                contentDescription = null,
                                                tint = colors.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = feature.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (feature.desc.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = feature.desc,
                                                    fontSize = 11.sp,
                                                    color = colors.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Download status / Progress bar
                        if (state.downloading) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { state.downloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = colors.primary,
                                    trackColor = colors.surfaceContainerHighest
                                )
                                val totalBytes = release?.apk?.size ?: 0L
                                val progressPercent = (state.downloadProgress * 100).toInt()
                                val progressDetail = if (totalBytes > 0) {
                                    val currentBytes = (totalBytes * state.downloadProgress).toLong()
                                    "$progressPercent% (${formatFileSize(currentBytes)} / ${formatFileSize(totalBytes)})"
                                } else {
                                    "$progressPercent%"
                                }
                                Text(
                                    text = "${text("ダウンロード中…")} $progressDetail",
                                    fontSize = 12.sp,
                                    color = colors.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        } else if (state.readyApk != null) {
                            Text(
                                text = text("ダウンロードが完了しました。インストールして更新できます"),
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (canDownload) {
                            Text(
                                text = text("Androidの確認画面でインストールします。初回は、このアプリからのインストールを許可してください"),
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (release != null) {
                            Text(
                                text = text(
                                    if (BuildConfig.DEBUG)
                                        "デバッグ版はアプリ内更新に対応していません。配布ページから正式版を入手してください"
                                    else
                                        "このリリースのAPKが見つかりません。配布ページを確認してください"
                                ),
                                fontSize = 11.sp,
                                color = colors.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = text(message),
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Fixed Footer: Actions
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!state.downloading) {
                            Button(
                                onClick = {
                                    when {
                                        state.isTestPreview && state.readyApk != null -> {
                                            state.dismiss()
                                        }
                                        state.readyApk != null -> install()
                                        canDownload -> state.download()
                                        release != null -> { openRelease(); state.dismiss() }
                                        else -> state.dismiss()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                )
                            ) {
                                Text(
                                    text = text(
                                        when {
                                            state.readyApk != null -> "インストール"
                                            canDownload -> "ダウンロード"
                                            release != null -> "配布ページを開く"
                                            else -> "閉じる"
                                        }
                                    ),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Secondary actions row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.downloading) {
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(onClick = { state.cancelDownload() }) {
                                    Text(text("中止"), color = colors.error, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                if (canDownload && release != null) {
                                    TextButton(onClick = openRelease) {
                                        Text(text("配布ページを開く"), color = colors.onSurfaceVariant, fontSize = 13.sp)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                TextButton(onClick = { state.dismiss() }) {
                                    Text(
                                        text = text(if (release != null) "後で" else "閉じる"),
                                        color = colors.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
