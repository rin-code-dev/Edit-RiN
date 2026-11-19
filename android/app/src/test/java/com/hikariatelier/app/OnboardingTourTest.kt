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

class OnboardingTourTest {
    @Test fun everyLanguageHasTheSameFiveCompleteSteps() {
        assertEquals(5, ONBOARDING_STEP_COUNT)
        for (language in listOf("ja", "en", "zh")) {
            val steps = onboardingSteps(language)
            assertEquals(5, steps.size)
            assertTrue(steps.all { it.title.isNotBlank() && it.body.isNotBlank() })
            assertEquals(steps.size, steps.map { it.title }.distinct().size)
        }
        val jaSteps = onboardingSteps("ja")
        assertEquals("作品を開いて動かす", jaSteps[0].title)
        assertEquals("ブロックとコードで編集する", jaSteps[1].title)
        assertEquals("コメントで値を確かめる・動かす", jaSteps[2].title)
        assertEquals("履歴の復元と画像の書き出し", jaSteps[3].title)
        assertEquals("制作をはじめましょう", jaSteps[4].title)

        val enSteps = onboardingSteps("en")
        assertEquals("Open and run a sketch", enSteps[0].title)
        assertEquals("Switch between blocks and code", enSteps[1].title)
        assertEquals("Inspect and tune with comments", enSteps[2].title)
        assertEquals("Restore history and export", enSteps[3].title)
        assertEquals("Start creating", enSteps[4].title)

        val zhSteps = onboardingSteps("zh")
        assertEquals("打开并运行作品", zhSteps[0].title)
        assertEquals("积木与代码自由切换", zhSteps[1].title)
        assertEquals("用注释查看变量与调节参数", zhSteps[2].title)
        assertEquals("历史恢复与作品导出", zhSteps[3].title)
        assertEquals("开始使用", zhSteps[4].title)

        // Unsupported languages use the English source text, like the user guide.
        assertEquals(onboardingSteps("en"), onboardingSteps("unsupported"))
        assertNotEquals(onboardingSteps("en"), onboardingSteps("ja"))
        assertNotEquals(onboardingSteps("en"), onboardingSteps("zh"))
    }

    @Test fun tourIsDueOnlyBeforeCompletionAndOnlyWhenWorksAreReady() {
        assertTrue(onboardingDue(0, loadFailed = false, hasPendingDraft = false, hasStorageFolder = true))
        assertFalse(onboardingDue(ONBOARDING_VERSION, loadFailed = false, hasPendingDraft = false, hasStorageFolder = true))
        assertFalse(onboardingDue(0, loadFailed = true, hasPendingDraft = false, hasStorageFolder = true))
        assertFalse(onboardingDue(0, loadFailed = false, hasPendingDraft = true, hasStorageFolder = true))
        // The first-run tour runs before folder selection prompt
        assertTrue(onboardingDue(0, loadFailed = false, hasPendingDraft = false, hasStorageFolder = false))
        // Existing users with storedVersion > 0 do not see onboarding again automatically.
        assertFalse(onboardingDue(1, loadFailed = false, hasPendingDraft = false, hasStorageFolder = true))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingTourUiTest {
    @get:Rule(order = 0) val activityRegistration = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).addActivityIfNotPresent(
                ComponentName(context, ComponentActivity::class.java)
            )
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    private fun title(language: String, step: Int) = onboardingSteps(language)[step].title

    @Test fun nextWalksThroughAllStepsAndStartFinishesOnce() {
        var finished = 0
        var skipped = 0
        compose.setContent {
            MaterialTheme { OnboardingTour("en", { finished++ }, { skipped++ }, {}, animate = false) }
        }
        compose.onNodeWithTag("onboarding-back").assertDoesNotExist()
        for (step in 0 until ONBOARDING_STEP_COUNT - 1) {
            compose.onNodeWithTag("onboarding-title").assertTextEquals(title("en", step))
            compose.onNodeWithTag("onboarding-next").performClick()
        }
        compose.onNodeWithTag("onboarding-title").assertTextEquals(title("en", ONBOARDING_STEP_COUNT - 1))
        compose.onNodeWithTag("onboarding-guide").assertExists()
        compose.onNodeWithTag("onboarding-try-sample").assertExists()
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.runOnIdle {
            assertEquals(1, finished)
            assertEquals(0, skipped)
        }
    }

    @Test fun backReturnsAndSkipEndsFromAnyStep() {
        var finished = 0
        var skipped = 0
        compose.setContent {
            MaterialTheme { OnboardingTour("ja", { finished++ }, { skipped++ }, {}, animate = false) }
        }
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithTag("onboarding-title").assertTextEquals(title("ja", 2))
        compose.onNodeWithTag("onboarding-back").performClick()
        compose.onNodeWithTag("onboarding-title").assertTextEquals(title("ja", 1))
        compose.onNodeWithTag("onboarding-skip").performClick()
        compose.runOnIdle {
            assertEquals(0, finished)
            assertEquals(1, skipped)
        }
    }

    @Test fun skipButtonHiddenWhenAllowSkipIsFalse() {
        compose.setContent {
            MaterialTheme { OnboardingTour("en", {}, {}, {}, allowSkip = false, animate = false) }
        }
        compose.onNodeWithTag("onboarding-skip").assertDoesNotExist()
    }

    @Test fun skipButtonShownWhenAllowSkipIsTrue() {
        compose.setContent {
            MaterialTheme { OnboardingTour("en", {}, {}, {}, allowSkip = true, animate = false) }
        }
        compose.onNodeWithTag("onboarding-skip").assertExists()
    }

    @Test fun forcedTourDisallowsSkipAndHidesBackButtonOnStepZero() {
        var finished = 0
        var skipped = 0
        compose.setContent {
            MaterialTheme { OnboardingTour("ja", { finished++ }, { skipped++ }, {}, allowSkip = false, animate = false) }
        }
        compose.onNodeWithTag("onboarding-skip").assertDoesNotExist()
        compose.onNodeWithTag("onboarding-back").assertDoesNotExist()
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithTag("onboarding-back").assertExists()
        compose.onNodeWithTag("onboarding-back").performClick()
        compose.onNodeWithTag("onboarding-back").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, finished)
            assertEquals(0, skipped)
        }
    }

    @Test fun detailedGuideLinkOnlyAppearsOnTheLastStep() {
        var opened = 0
        compose.setContent {
            MaterialTheme { OnboardingTour("zh", {}, {}, { opened++ }, animate = false) }
        }
        compose.onNodeWithTag("onboarding-guide").assertDoesNotExist()
        repeat(ONBOARDING_STEP_COUNT - 1) { compose.onNodeWithTag("onboarding-next").performClick() }
        compose.onNodeWithTag("onboarding-guide").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun practiceScreensRunWithoutAnyAppState() {
        compose.setContent {
            MaterialTheme { OnboardingTour("en", {}, {}, {}, animate = false) }
        }
        // Step 0: Write, Run & Floating PiP
        compose.onNodeWithText("Palette").performScrollTo().performClick()
        compose.onNodeWithText("Top-R").performScrollTo().performClick()
        compose.onNodeWithText("Btm-L").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Toggle minimize").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Toggle minimize").performScrollTo().performClick()
        compose.onNodeWithText("Run").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding-next").performClick()

        // Step 1: Two-Way Block & Code Editing
        compose.onNodeWithContentDescription("Change block color").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Add block").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding-insert-ring-block").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Full-screen preview").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding-block-full-preview").assertExists()
        compose.onNodeWithTag("onboarding-next").performClick()

        // Step 2: Live Tuning with Comments
        compose.onNodeWithTag("onboarding-watch-badge").assertDoesNotExist()
        compose.onNodeWithTag("onboarding-watch-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding-watch-badge").assertExists()
        compose.onNodeWithTag("onboarding-live-slider").performScrollTo().assertExists()
        compose.onNodeWithText("triangle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Color 2").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding-next").performClick()

        // Step 3: Unified History, Export & Minify
        compose.onNodeWithContentDescription("Pause").performScrollTo().performClick()
        compose.onNodeWithContentDescription("History").performScrollTo().performClick()
        compose.onNodeWithText("Create snapshot").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Export image").performScrollTo().performClick()
        compose.onNodeWithText("Share").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("onboarding-golf-button").performScrollTo().performClick()
        compose.onNodeWithText("88 / 280 chars (Compressed)").assertExists()
        compose.onNodeWithTag("onboarding-next").performClick()

        // Step 4: Ready to Create
        compose.onNodeWithText("Start Creating").assertExists()
        compose.onNodeWithTag("onboarding-try-sample").assertExists()
        compose.onNodeWithTag("onboarding-guide").assertExists()
    }
}
