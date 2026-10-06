package com.hikariatelier.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal data class ReleaseFeature(
    val iconRes: Int,
    val titleEn: String,
    val titleJa: String,
    val descEn: String,
    val descJa: String
)

internal data class ReleaseNote(
    val versionName: String,
    val versionCode: Int,
    val titleEn: String,
    val titleJa: String,
    val features: List<ReleaseFeature>
)

internal val APP_RELEASE_NOTES = listOf(
    ReleaseNote(
        versionName = "2.2.2",
        versionCode = 29,
        titleEn = "What's New in v2.2.2",
        titleJa = "v2.2.2 の新機能",
        features = listOf(
            ReleaseFeature(
                iconRes = R.drawable.ic_tune,
                titleEn = "Live Parameters Button",
                titleJa = "パラメータボタンの独立",
                descEn = "Access sketch parameters directly from the preview bar with a live count badge indicating adjustable inputs.",
                descJa = "プレビューバーからパラメータ調整へ直接アクセス。パラメータ数のリアルタイムバッジも表示。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_error_jump,
                titleEn = "Gutter Error Details",
                titleJa = "行番号エラー表示 & 詳細確認",
                descEn = "Error lines are highlighted in the gutter. Tap any line number with an error to immediately inspect the issue.",
                descJa = "エラー行を行番号で強調表示。行番号をタップするだけでエラーメッセージの詳細をその場で確認可能。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_code,
                titleEn = "Smoother Editor Highlighting",
                titleJa = "高速・安定したコードハイライト",
                descEn = "Enjoy flicker-free editing with incremental syntax caching and responsive spring-based panel animations.",
                descJa = "キャッシュ機構により入力中のチラつきを抑え、各種パネルの開閉も滑らかなスプリングアニメーションに刷新。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_camera,
                titleEn = "WebView & Media Tuning",
                titleJa = "動画・キャンバス描画の最適化",
                descEn = "Resolved WebView video poster issues and accelerated canvas pixel operations for camera and generative media.",
                descJa = "WebViewの動画エラーを防止し、カメラや画像処理を行うスケッチのキャンバス描画パフォーマンスを改善。"
            )
        )
    ),
    ReleaseNote(
        versionName = "2.2.1",
        versionCode = 28,
        titleEn = "What's New in v2.2.1",
        titleJa = "v2.2.1 の新機能",
        features = listOf(
            ReleaseFeature(
                iconRes = R.drawable.ic_folder_code,
                titleEn = "Folders & Organization",
                titleJa = "作品フォルダー機能",
                descEn = "Organize user works with 1-level folders. Create, rename, move, and drag to reorder folders effortlessly.",
                descJa = "1階層のフォルダーで作品を分類・整理。フォルダー作成・名前変更・移動やドラッグでの並び替えに対応。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_tune,
                titleEn = "Protected Official Samples",
                titleJa = "公式サンプルの保護・刷新",
                descEn = "Official samples are kept safe in a read-only section. Freely test parameters and copy anytime to edit.",
                descJa = "公式サンプル原本を保護。パラメータを自由に動かして試し、コピーして自分用に編集可能。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_search,
                titleEn = "Inline Search & Replace",
                titleJa = "行内 検索・置換バー",
                descEn = "A compact search and replace bar directly above the keyboard with match count and highlighting.",
                descJa = "キーボード直上に常駐するコンパクトな検索バー。ヒット数・強調表示・置換に対応。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_rename,
                titleEn = "Asset Rename & Refactoring",
                titleJa = "素材名と参照コードの一括置換",
                descEn = "Rename project assets with automatic updates to code references across your project files.",
                descJa = "素材（画像・音声等）の名前変更時に、コード内の参照パス文字列も一括で安全に置換。"
            )
        )
    ),
    ReleaseNote(
        versionName = "2.2.0",
        versionCode = 27,
        titleEn = "What's New in v2.2.0",
        titleJa = "v2.2.0 の新機能",
        features = listOf(
            ReleaseFeature(
                iconRes = R.drawable.ic_code,
                titleEn = "WebGPU Support",
                titleJa = "WebGPU サポート",
                descEn = "Experience next-generation high-performance graphics on compatible devices with p5.js WebGPU mode.",
                descJa = "p5.js WebGPU モードに対応。対応端末で次世代の高性能グラフィックス描画を楽しめます。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_camera,
                titleEn = "Device Camera & Microphone",
                titleJa = "カメラ・マイク対応",
                descEn = "Build reactive sketches using live camera video feeds and real-time audio analysis with new Camera & Microphone samples.",
                descJa = "端末のカメラ映像やマイク音声入力と連動する作品を制作可能に。Camera・Microphone サンプルも追加。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_sort,
                titleEn = "Refreshed Selection UI",
                titleJa = "選択UIの刷新",
                descEn = "A cleaner work gallery layout, revamped sort menus, and smoother dialog interactions for effortless navigation.",
                descJa = "作品一覧やソートメニューのレイアウトを刷新し、操作性と視認性が向上しました。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_tune,
                titleEn = "Stabilized Audio Analysis",
                titleJa = "オーディオ解析の安定化",
                descEn = "Added new sample sketches to explore and showcase newly introduced features.",
                descJa = "新機能を確認出来るサンプル作品の追加。"
            )
        )
    )
)

@Composable
internal fun ReleaseNotesDialog(
    note: ReleaseNote = APP_RELEASE_NOTES.first(),
    language: String = "system",
    onDismiss: () -> Unit,
    windowSetup: @Composable () -> Unit = {}
) {
    windowSetup()
    val isJa = language == "ja" || (language == "system" && java.util.Locale.getDefault().language == "ja")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF27272A), RoundedCornerShape(16.dp)),
            color = Color(0xFF101014),
            contentColor = Color(0xFFE4E4E7)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF27272A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_history),
                            contentDescription = null,
                            tint = Color(0xFFA8C7FA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isJa) note.titleJa else note.titleEn,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isJa) "最新のアップデートと新機能" else "Latest updates and enhancements",
                            fontSize = 12.sp,
                            color = Color(0xFFA1A1AA)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Close",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF27272A), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Feature List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(note.features) { feature ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF18181B))
                                .padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF27272A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(feature.iconRes),
                                    contentDescription = null,
                                    tint = Color(0xFFA8C7FA),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isJa) feature.titleJa else feature.titleEn,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isJa) feature.descJa else feature.descEn,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFFA1A1AA)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Action
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA8C7FA),
                        contentColor = Color(0xFF09090B)
                    )
                ) {
                    Text(
                        text = if (isJa) "はじめる" else "Get Started",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
