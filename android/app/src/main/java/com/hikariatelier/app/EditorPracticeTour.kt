package com.hikariatelier.app

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

internal const val PRACTICE_TOUR_STEP_COUNT = 4

internal data class PracticeTourStep(val title: String, val body: String)

internal fun practiceTourSteps(language: String): List<PracticeTourStep> {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    return listOf(
        PracticeTourStep(
            t("変数の値をリアルタイムにみる", "Watch variable values live", "实时查看变量值"),
            t("コードの行末に // ? を書くと、実行中の値がエディター上にバッジとしてリアルタイム表示されます。",
                "Add // ? at the end of a line to see running values appear right in the editor as a live badge.",
                "在代码行末添加 // ?，运行中的数值就会以徽标形式在编辑器中实时显示。")
        ),
        PracticeTourStep(
            t("スライダーでコードと図形を同時に操る", "Tune code and shapes simultaneously", "用滑块同步调节代码与图形"),
            t("// @slider や // @color を添えると、操作シートが出現。スライダーを動かすだけで、プレビューとコード内の数値が同時に書き換わります。",
                "Add // @slider or // @color to show controls. Dragging sliders instantly updates both the preview and code literals.",
                "添加 // @slider 或 // @color 即可调出控制面板。拖动滑块即可同时更新预览图形和代码中的数值。")
        ),
        PracticeTourStep(
            t("作品の設定パネルを作る", "Create custom sketch parameters", "创建作品专属设置面板"),
            t("コード冒頭に // @rin を書くと、作品ごとの設定項目を整理して定義できます。値は作品に保存され、共有時にも引き継がれます。",
                "Declare // @rin at the top of your code to build a custom settings panel. Values are saved with the sketch and persist across sharing.",
                "在代码开头声明 // @rin 即可创建自定义参数面板。参数值随作品一同保存，分享时也会保留。")
        ),
        PracticeTourStep(
            t("投稿用にコードを圧縮する", "Minify code for sharing", "压缩代码以便分享"),
            t("SNS（Twitter/X）に1投稿で収まるようコードを自動でスリム化します。// #つぶやきProcessing などのハッシュタグも自動で保持されます。",
                "Compresses your code to fit social media character limits while cleanly preserving hashtags like // #つぶやきProcessing.",
                "自动压缩代码以适应社交平台字数限制，并智能保留 // #つぶやきProcessing 等标签。")
        )
    )
}

/**
 * True when the editor practice tour is due on an app update (onboarding already completed in earlier versions,
 * but this version's practice tour has not been seen yet).
 */
internal fun practiceTourDue(
    onboardingVersion: Int,
    practiceTourVersion: Int,
    targetVersionCode: Int = BuildConfig.VERSION_CODE,
    loadFailed: Boolean = false,
    hasPendingDraft: Boolean = false
): Boolean = onboardingVersion > 0 && practiceTourVersion < targetVersionCode && !loadFailed && !hasPendingDraft

internal fun practiceTourSampleTitle(language: String): String =
    when (language) {
        "zh" -> "教程：注释与实时功能"
        "en" -> "Tutorial: Comments & Live Features"
        else -> "チュートリアル: コメント機能"
    }

internal fun practiceTourSampleCode(language: String): String =
    when (language) {
        "zh" -> """
// @rin choice shape "图形" circle square triangle
// @rin boolean animate "旋转动画" true

let size = 120; // @slider(40, 200, 10) "大小"
let ink = "#D67856"; // @color "颜色"

function setup() {
  createCanvas(400, 400);
  rectMode(CENTER);
}

function draw() {
  background('#F2EFE7');

  let frame = frameCount; // ?

  translate(width / 2, height / 2);
  if (rinParams.animate) {
    rotate(frame * 0.02);
  }

  fill(ink);
  noStroke();

  if (rinParams.shape === 'square') {
    rect(0, 0, size, size, 16);
  } else if (rinParams.shape === 'triangle') {
    triangle(0, -size * 0.55, size * 0.5, size * 0.45, -size * 0.5, size * 0.45);
  } else {
    circle(0, 0, size);
  }
}
""".trimIndent()
        "en" -> """
// @rin choice shape "Shape" circle square triangle
// @rin boolean animate "Animate Rotation" true

let size = 120; // @slider(40, 200, 10) "Size"
let ink = "#D67856"; // @color "Color"

function setup() {
  createCanvas(400, 400);
  rectMode(CENTER);
}

function draw() {
  background('#F2EFE7');

  let frame = frameCount; // ?

  translate(width / 2, height / 2);
  if (rinParams.animate) {
    rotate(frame * 0.02);
  }

  fill(ink);
  noStroke();

  if (rinParams.shape === 'square') {
    rect(0, 0, size, size, 16);
  } else if (rinParams.shape === 'triangle') {
    triangle(0, -size * 0.55, size * 0.5, size * 0.45, -size * 0.5, size * 0.45);
  } else {
    circle(0, 0, size);
  }
}
""".trimIndent()
        else -> """
// @rin choice shape "図形" circle square triangle
// @rin boolean animate "回転アニメーション" true

let size = 120; // @slider(40, 200, 10) "サイズ"
let ink = "#D67856"; // @color "カラー"

function setup() {
  createCanvas(400, 400);
  rectMode(CENTER);
}

function draw() {
  background('#F2EFE7');

  let frame = frameCount; // ?

  translate(width / 2, height / 2);
  if (rinParams.animate) {
    rotate(frame * 0.02);
  }

  fill(ink);
  noStroke();

  if (rinParams.shape === 'square') {
    rect(0, 0, size, size, 16);
  } else if (rinParams.shape === 'triangle') {
    triangle(0, -size * 0.55, size * 0.5, size * 0.45, -size * 0.5, size * 0.45);
  } else {
    circle(0, 0, size);
  }
}
""".trimIndent()
    }

/**
 * Full-screen Editor Practice Tour.
 * Hands-on demonstration of Edit:RiN's unique comment-based features in a sandbox environment.
 */
@Composable
internal fun EditorPracticeTour(
    language: String,
    onClose: () -> Unit,
    onTrySample: () -> Unit,
    animate: Boolean = true
) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val steps = remember(language) { practiceTourSteps(language) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = step == steps.lastIndex
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler {
        if (step > 0) {
            step--
        } else {
            onClose()
        }
    }

    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(steps.size) { index ->
                    Box(
                        Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index <= step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                            )
                    )
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.testTag("practice-tour-close")) {
                Text(t("閉じる", "Close", "关闭"))
            }
        }
    }

    val mock: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxSize().semantics { contentDescription = t("実践用の画面", "Practice screen", "实践练习画面") },
            contentAlignment = Alignment.Center
        ) {
            key(step) { EditorPracticeMock(step, language, animate) }
        }
    }

    val text: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                t("実践ツアー ${step + 1} / ${steps.size}", "Practice Tour ${step + 1} of ${steps.size}", "实践向导 第 ${step + 1} 步，共 ${steps.size} 步"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                steps[step].title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag("practice-tour-title")
            )
            Text(
                steps[step].body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    val buttons: @Composable () -> Unit = {
        if (!last) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("practice-tour-back")
                    ) {
                        Text(t("戻る", "Back", "返回"))
                    }
                }
                Button(
                    onClick = { step++ },
                    modifier = Modifier.weight(if (step > 0) 2f else 1f).heightIn(min = 48.dp).testTag("practice-tour-next")
                ) {
                    Text(t("次へ", "Next", "下一步"))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { step-- },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("practice-tour-back")
                ) {
                    Text(t("戻る", "Back", "返回"))
                }
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("practice-tour-finish")
                ) {
                    Text(t("終了", "Finish", "完成"))
                }
                Button(
                    onClick = onTrySample,
                    modifier = Modifier.weight(1.5f).heightIn(min = 48.dp).testTag("practice-tour-try-sample"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_reload),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(t("ためす", "Try in Editor", "尝试示例"))
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize().testTag("editor-practice-tour"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier.fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            header()
            Spacer(Modifier.height(12.dp))
            if (landscape) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { mock() }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        text()
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
