package com.hikariatelier.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsAboutSectionTest {
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

    @Test
    fun aboutSectionRendersAppInfoAndActionRowsWithoutCrash() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val updateVm = UpdateViewModel(app)
        var guideShown = false
        var onboardingShown = false
        var licensesShown = false
        var releaseNotesShown = false
        var openedUrl: String? = null

        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsAboutSection(
                        updateViewModel = updateVm,
                        onShowGuide = { guideShown = true },
                        onShowOnboarding = { onboardingShown = true },
                        onShowLicenses = { licensesShown = true },
                        onShowReleaseNotes = { releaseNotesShown = true },
                        openExternalUrl = { openedUrl = it },
                        textTranslator = { s, args ->
                            if (args.isEmpty()) s else java.lang.String.format(java.util.Locale.ROOT, s, *args)
                        }
                    )
                }
            }
        }

        compose.onNodeWithText("Edit:RiN").assertExists()
        compose.onNodeWithText("アップデートを確認").assertExists()
        compose.onNodeWithText("公式サイト").assertExists()
        compose.onNodeWithText("使い方ガイド").assertExists()

        // Test interaction
        compose.onNodeWithText("使い方ガイド").performScrollTo().performClick()
        assertTrue(guideShown)

        compose.onNodeWithText("公式サイト").performScrollTo().performClick()
        assertEquals(OFFICIAL_WEBSITE_URL, openedUrl)
    }
}
