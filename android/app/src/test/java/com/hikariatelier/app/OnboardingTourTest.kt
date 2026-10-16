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
    @Test fun everyLanguageHasTheSameEightCompleteSteps() {
        assertEquals(8, ONBOARDING_STEP_COUNT)
        for (language in listOf("ja", "en", "zh")) {
            val steps = onboardingSteps(language)
            assertEquals(8, steps.size)
            assertTrue(steps.all { it.title.isNotBlank() && it.body.isNotBlank() })
            assertEquals(steps.size, steps.map { it.title }.distinct().size)
        }
        val jaSteps = onboardingSteps("ja")
        assertEquals("記号と数値の調整", jaSteps[2].title)
        assertEquals("PiP小窓プレビュー", jaSteps[3].title)

        val enSteps = onboardingSteps("en")
        assertEquals("Symbols and scrubbing", enSteps[2].title)
        assertEquals("PiP floating preview", enSteps[3].title)

        val zhSteps = onboardingSteps("zh")
        assertEquals("符号输入与数值微调", zhSteps[2].title)
        assertEquals("画中画悬浮预览", zhSteps[3].title)

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
        // The first-run tour now runs before folder selection prompt
        assertTrue(onboardingDue(0, loadFailed = false, hasPendingDraft = false, hasStorageFolder = false))
        // Existing users with storedVersion > 0 do not see onboarding again; they are routed to practice tour instead.
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
        // Each mock screen is built from the language alone: touch controls on every step.
        // Step 0: Gallery
        compose.onNodeWithText("Palette").performClick()
        compose.onNodeWithTag("onboarding-next").performClick()
        // Step 1: Run
        compose.onNodeWithText("Run").performClick()
        compose.onNodeWithTag("onboarding-next").performClick()
        // Step 2: Editor Tools (Symbols and scrubbing)
        compose.onNodeWithText("{ }").performClick()
        compose.onNodeWithContentDescription("Color 3").performClick()
        compose.onNodeWithText("80").performClick()
        compose.onNodeWithTag("onboarding-next").performClick()
        // Step 3: PiP Floating Preview
        compose.onNodeWithText("Top-R").performClick()
        compose.onNodeWithText("Btm-L").performClick()
        compose.onNodeWithText("M").performClick()
        compose.onNodeWithText("Opacity").performClick()
        compose.onNodeWithContentDescription("Toggle minimize").performClick()
        compose.onNodeWithContentDescription("Toggle minimize").performClick()
        compose.onNodeWithTag("onboarding-next").performClick()
        // Step 4: Parameters
        compose.onNodeWithContentDescription("Color 2").performClick()
        compose.onNodeWithTag("onboarding-next").performClick()
        // Step 5: Pause and History
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.onNodeWithContentDescription("History").performClick()
        compose.onNodeWithText("Create snapshot").performClick()
        compose.onNodeWithTag("onboarding-next").performClick()
        // Step 6: Capture and Share
        compose.onNodeWithContentDescription("Export image").performClick()
        compose.onNodeWithText("Share").assertIsEnabled()
        compose.onNodeWithTag("onboarding-next").performClick()
        // Step 7: Make it yours (Final step)
        compose.onNodeWithText("Start Creating").assertExists()
        compose.onNodeWithTag("onboarding-guide").assertExists()
    }
}
