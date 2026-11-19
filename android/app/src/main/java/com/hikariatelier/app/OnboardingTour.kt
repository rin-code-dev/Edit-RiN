package com.hikariatelier.app

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Raise this only when the tour is redesigned and should be shown again to everyone. */
internal const val ONBOARDING_VERSION = 3

internal const val ONBOARDING_STEP_COUNT = 5

internal data class OnboardingStep(val title: String, val body: String)

/** Tour copy is kept here, in the same ja/en/zh form as the user guide. */
internal fun onboardingSteps(language: String): List<OnboardingStep> {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    return listOf(
        OnboardingStep(
            t("作品を開いて動かす", "Open and run a sketch", "打开并运行作品"),
            t("作品一覧からサンプルを開き、「実行」でプレビューを更新します。縦画面でコードを書くときは、小窓（PiP）にプレビューを表示したまま編集できます。",
                "Pick a sample from the gallery and press Run to update the canvas. In portrait mode, the floating PiP window keeps your sketch visible while you type.",
                "从作品库选择示例，点击“运行”更新画面。竖屏编写代码时，可通过画中画小窗随时查看运行效果。")
        ),
        OnboardingStep(
            t("ブロックとコードで編集する", "Switch between blocks and code", "积木与代码自由切换"),
            t("sketch.js は「コード」と「ブロック」をいつでも切り替えられます。ブロック内の色や数値をタップして書き換えても、元のコードはそのまま保たれます。",
                "Switch sketch.js between Code and Blocks at any time. Tap colors or numbers inside a block to edit them directly—your JavaScript source stays intact.",
                "sketch.js 可随时在“代码”和“积木”之间切换。直接点击积木中的颜色或数字即可修改，原始代码始终保持完整。")
        ),
        OnboardingStep(
            t("コメントで値を確かめる・動かす", "Inspect and tune with comments", "用注释查看变量与调节参数"),
            t("行末に // ? を書くと実行中の値が表示されます。// @slider や // @color、先頭の // @rin を書くと、スライダーや色選択で作品を動かしながら調整できます。",
                "Add // ? at the end of a line to see its live value. Use // @slider, // @color, or // @rin comments to add sliders and color pickers without changing how your code runs.",
                "在行末写上 // ? 可直接显示运行时的值。添加 // @slider、// @color 或 // @rin 注释，即可用滑块和取色器实时调整画面。")
        ),
        OnboardingStep(
            t("履歴の復元と画像の書き出し", "Restore history and export", "历史恢复与作品导出"),
            t("「履歴」から自動保存やスナップショットを見比べて以前の状態に戻せます。高解像度のPNG保存やMP4・GIF録画、SNS投稿用のコード短縮にも対応しています。",
                "Compare auto-saves and snapshots in History whenever you want to roll back. Export up to 4x PNG, record MP4 or GIF, or shorten code for social posts.",
                "在“历史”中可对比自动保存记录与快照并恢复旧版本。支持导出最高 4 倍分辨率 PNG、录制 MP4/GIF，以及缩短代码以便分享。")
        ),
        OnboardingStep(
            t("制作をはじめましょう", "Start creating", "开始使用"),
            t("このツアーや詳しい使い方は、設定画面の「アプリ情報」や「使い方ガイド」からいつでも開けます。",
                "You can reopen this tour or browse the User Guide anytime from Settings.",
                "您可以随时在“设置”中重新打开本向导或查看使用指南。")
        )
    )
}

/**
 * True when the first-run tour is due on a fresh install (storedVersion == 0).
 * Never shown over an ongoing load failure or a pending draft recovery.
 */
internal fun onboardingDue(
    storedVersion: Int,
    loadFailed: Boolean = false,
    hasPendingDraft: Boolean = false,
    hasStorageFolder: Boolean = true
): Boolean = storedVersion == 0 && !loadFailed && !hasPendingDraft

/**
 * Full-screen unified onboarding tour. Every interaction lives in the shared studio mock of
 * [OnboardingMocks]; nothing here reads or writes real works, files, or settings.
 */
@Composable
internal fun OnboardingTour(
    language: String,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    onOpenDetailedGuide: () -> Unit,
    allowSkip: Boolean = true,
    animate: Boolean = true,
    onOpenPracticeTour: () -> Unit = {},
    onTrySample: (() -> Unit)? = null
) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val steps = remember(language) { onboardingSteps(language) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val safeStep = step.coerceIn(0, steps.lastIndex)
    val last = safeStep == steps.lastIndex
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val studioState = remember { OnboardingStudioState() }

    BackHandler {
        if (safeStep > 0) {
            step = safeStep - 1
        } else if (allowSkip) {
            onSkip()
        }
    }

    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(steps.size) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .clickable { step = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (index <= safeStep) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                                )
                        )
                    }
                }
            }
            if (allowSkip) {
                TextButton(onClick = onSkip, modifier = Modifier.testTag("onboarding-skip")) {
                    Text(t("スキップ", "Skip", "跳过"))
                }
            }
        }
    }
    val mock: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxSize().semantics { contentDescription = t("練習用の画面", "Practice screen", "练习画面") },
            contentAlignment = Alignment.Center
        ) {
            key(safeStep) { OnboardingMock(safeStep, language, animate, studioState) }
        }
    }
    val text: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                t("ステップ ${safeStep + 1} / ${steps.size}", "Step ${safeStep + 1} of ${steps.size}", "第 ${safeStep + 1} 步，共 ${steps.size} 步"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                steps[safeStep].title,
                style = if (landscape) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag("onboarding-title")
            )
            Text(
                steps[safeStep].body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    val buttons: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (safeStep > 0) {
                    OutlinedButton(
                        onClick = { step = safeStep - 1 },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("onboarding-back")
                    ) {
                        Text(t("戻る", "Back", "返回"))
                    }
                }
                Button(
                    onClick = { if (last) onFinish() else step = safeStep + 1 },
                    modifier = Modifier.weight(if (safeStep > 0) 2f else 1f).heightIn(min = 48.dp).testTag("onboarding-next")
                ) {
                    Text(if (last) t("始める", "Start Creating", "开始创作") else t("次へ", "Next", "下一步"))
                }
            }
            if (last) {
                val sampleAction = onTrySample ?: onOpenPracticeTour
                FilledTonalButton(
                    onClick = sampleAction,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("onboarding-try-sample")
                ) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("サンプル作品をエディターで開く", "Open sample in editor", "在编辑器中打开示例"), maxLines = 1)
                }
                TextButton(
                    onClick = onOpenDetailedGuide,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding-guide")
                ) {
                    Text(t("詳しい使い方を見る", "Open the User Guide", "查看使用指南"))
                }
            }
        }
    }

    Surface(Modifier.fillMaxSize().testTag("onboarding-tour"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            header()
            Spacer(Modifier.height(12.dp))
            if (landscape) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { mock() }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        text()
                        Spacer(Modifier.height(12.dp))
                        buttons()
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) { mock() }
                Spacer(Modifier.height(12.dp))
                text()
                Spacer(Modifier.height(16.dp))
                buttons()
            }
        }
    }
}
