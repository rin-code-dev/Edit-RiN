package com.hikariatelier.app

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class EditorPracticeTourStepsTest {
    @Test
    fun everyLanguageHasTheSameFourCompleteSteps() {
        assertEquals(4, PRACTICE_TOUR_STEP_COUNT)
        for (language in listOf("ja", "en", "zh")) {
            val steps = practiceTourSteps(language)
            assertEquals(4, steps.size)
            assertTrue(steps.all { it.title.isNotBlank() && it.body.isNotBlank() })
            assertEquals(steps.size, steps.map { it.title }.distinct().size)
        }
        val jaSteps = practiceTourSteps("ja")
        assertEquals("変数の値をリアルタイムにみる", jaSteps[0].title)
        assertEquals("スライダーでコードと図形を同時に操る", jaSteps[1].title)
        assertEquals("作品の設定パネルを作る", jaSteps[2].title)
        assertEquals("コードを極限まで短くする", jaSteps[3].title)

        val enSteps = practiceTourSteps("en")
        assertEquals("Watch variable values live", enSteps[0].title)
        assertEquals("Tune code and shapes simultaneously", enSteps[1].title)
        assertEquals("Create custom sketch parameters", enSteps[2].title)
        assertEquals("Minify code for sharing", enSteps[3].title)

        val zhSteps = practiceTourSteps("zh")
        assertEquals("实时查看变量值", zhSteps[0].title)
        assertEquals("用滑块同步调节代码与图形", zhSteps[1].title)
        assertEquals("创建作品专属设置面板", zhSteps[2].title)
        assertEquals("极简压缩代码以便分享", zhSteps[3].title)

        // Fallback for unsupported languages uses English
        assertEquals(practiceTourSteps("en"), practiceTourSteps("unsupported"))
    }

    @Test
    fun sampleCodeContainsAllTourFeatures() {
        for (language in listOf("ja", "en", "zh")) {
            val title = practiceTourSampleTitle(language)
            val code = practiceTourSampleCode(language)

            assertTrue("Title should not be blank", title.isNotBlank())
            assertTrue("Code should contain // @rin declaration", code.contains("// @rin"))
            assertTrue("Code should contain // @slider", code.contains("// @slider"))
            assertTrue("Code should contain // @color", code.contains("// @color"))
            assertTrue("Code should contain // ?", code.contains("// ?"))
            assertTrue("Code should read rinParams", code.contains("rinParams."))
            assertTrue("Code should support triangle shape option", code.contains("triangle"))
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorPracticeTourUiTest {
    @get:Rule(order = 0)
    val activityRegistration = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).addActivityIfNotPresent(
                ComponentName(context, ComponentActivity::class.java)
            )
        }
    }

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun title(language: String, step: Int) = practiceTourSteps(language)[step].title

    @Test
    fun nextWalksThroughAllFourStepsAndFinishEnds() {
        var closed = 0
        var triedSample = 0
        compose.setContent {
            MaterialTheme {
                EditorPracticeTour(
                    language = "ja",
                    onClose = { closed++ },
                    onTrySample = { triedSample++ },
                    animate = false
                )
            }
        }

        // Step 0
        compose.onNodeWithTag("practice-tour-back").assertDoesNotExist()
        compose.onNodeWithTag("practice-tour-title").assertTextEquals(title("ja", 0))

        // Step 0 -> Step 1
        compose.onNodeWithTag("practice-tour-next").performClick()
        compose.onNodeWithTag("practice-tour-title").assertTextEquals(title("ja", 1))
        compose.onNodeWithTag("practice-tour-back").assertExists()

        // Step 1 -> Step 2
        compose.onNodeWithTag("practice-tour-next").performClick()
        compose.onNodeWithTag("practice-tour-title").assertTextEquals(title("ja", 2))

        // Step 2 -> Step 3 (final)
        compose.onNodeWithTag("practice-tour-next").performClick()
        compose.onNodeWithTag("practice-tour-title").assertTextEquals(title("ja", 3))
        compose.onNodeWithTag("practice-tour-finish").assertExists()
        compose.onNodeWithTag("practice-tour-try-sample").assertExists()

        // Click "終了" (Finish)
        compose.onNodeWithTag("practice-tour-finish").performClick()
        compose.runOnIdle {
            assertEquals(1, closed)
            assertEquals(0, triedSample)
        }
    }

    @Test
    fun trySampleInvokesTrySampleCallback() {
        var closed = 0
        var triedSample = 0
        compose.setContent {
            MaterialTheme {
                EditorPracticeTour(
                    language = "en",
                    onClose = { closed++ },
                    onTrySample = { triedSample++ },
                    animate = false
                )
            }
        }

        // Advance to step 3
        compose.onNodeWithTag("practice-tour-next").performClick()
        compose.onNodeWithTag("practice-tour-next").performClick()
        compose.onNodeWithTag("practice-tour-next").performClick()

        // Click "Try Sample"
        compose.onNodeWithTag("practice-tour-try-sample").performClick()
        compose.runOnIdle {
            assertEquals(0, closed)
            assertEquals(1, triedSample)
        }
    }

    @Test
    fun backReturnsToPreviousStepAndCloseExits() {
        var closed = 0
        compose.setContent {
            MaterialTheme {
                EditorPracticeTour(
                    language = "ja",
                    onClose = { closed++ },
                    onTrySample = {},
                    animate = false
                )
            }
        }

        // Go forward 2 steps
        compose.onNodeWithTag("practice-tour-next").performClick()
        compose.onNodeWithTag("practice-tour-next").performClick()
        compose.onNodeWithTag("practice-tour-title").assertTextEquals(title("ja", 2))

        // Go back
        compose.onNodeWithTag("practice-tour-back").performClick()
        compose.onNodeWithTag("practice-tour-title").assertTextEquals(title("ja", 1))

        // Close via header button
        compose.onNodeWithTag("practice-tour-close").performClick()
        compose.runOnIdle {
            assertEquals(1, closed)
        }
    }

    @Test
    fun interactiveMocksRespondToUserInputsAcrossAllSteps() {
        compose.setContent {
            MaterialTheme {
                EditorPracticeTour(
                    language = "en",
                    onClose = {},
                    onTrySample = {},
                    animate = false
                )
            }
        }

        // Step 0: Variable Watch toggle
        compose.onNodeWithTag("practice-watch-badge").assertDoesNotExist()
        compose.onNodeWithTag("practice-watch-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("practice-watch-badge").assertExists()

        // Step 1: Inline slider & color chips
        compose.onNodeWithTag("practice-tour-next").performClick()
        compose.onNodeWithTag("practice-inline-slider").assertExists()
        compose.onNodeWithTag("practice-color-chip-1").performScrollTo().performClick()

        // Step 2: Rin parameter shapes & slider
        compose.onNodeWithTag("practice-tour-next").performClick()
        compose.onNodeWithTag("practice-rin-shape-triangle").performScrollTo().performClick()
        compose.onNodeWithTag("practice-rin-shape-square").performScrollTo().performClick()
        compose.onNodeWithTag("practice-rin-slider").assertExists()

        // Step 3: Tweet Golf minify toggle and localized character counter
        compose.onNodeWithTag("practice-tour-next").performClick()
        compose.onNodeWithText("158 / 280 chars").assertExists()
        compose.onNodeWithTag("practice-golf-button").performScrollTo().performClick()
        compose.onNodeWithText("88 / 280 chars").assertExists()
        compose.onNodeWithText("-70 chars (Compressed!)").assertExists()
        compose.onNodeWithTag("practice-golf-button").performScrollTo().performClick()
        compose.onNodeWithText("158 / 280 chars").assertExists()
    }
}
