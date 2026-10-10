package com.hikariatelier.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.ExternalResource
import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorFoldingUiTest {
    // Register the host only in Robolectric, so release tests need no activity in the shipped manifest.
    @get:Rule(order = 0) val activityRegistration = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).addActivityIfNotPresent(
                ComponentName(context, ComponentActivity::class.java)
            )
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    private fun verifyFoldScanWaitsForIdle(initial: String) {
        val parsed = java.util.concurrent.CopyOnWriteArrayList<String>()
        var value by mutableStateOf(TextFieldValue(initial))
        var result: EditorFoldSnapshot? = null
        val parser: (String) -> List<CodeFold> = { text ->
            parsed.add(text)
            codeFolds(text)
        }
        compose.mainClock.autoAdvance = false
        compose.setContent { result = editorFoldRegions(value, "typing", parser) }
        compose.mainClock.advanceTimeBy(EDITOR_FOLD_IDLE_MS + 32)
        compose.waitUntil(5000) {
            compose.mainClock.advanceTimeByFrame()
            parsed.size == 1 && result?.regions != null
        }
        // Each keystroke is faster than the idle window; none may start a full scan.
        repeat(12) {
            compose.runOnIdle {
                val position = value.text.length
                value = TextFieldValue(value.text + "x", androidx.compose.ui.text.TextRange(position + 1))
            }
            compose.mainClock.advanceTimeBy(64)
            assertEquals(1, parsed.size)
        }
        compose.mainClock.advanceTimeBy(EDITOR_FOLD_IDLE_MS + 32)
        compose.waitUntil(5000) {
            compose.mainClock.advanceTimeByFrame()
            parsed.size == 2
        }
        assertEquals(value.text, parsed.last())
    }

    @Test fun shortSourceIsParsedOnlyAfterTypingStops() = verifyFoldScanWaitsForIdle("function f() {\n run();\n}\n")

    @Test fun largeSourceIsParsedOnlyAfterTypingStops() = verifyFoldScanWaitsForIdle("function f() {\n run();\n}\n".repeat(500))

    private fun showEditor(source: String, readOnly: Boolean = true): EditorSessionViewModel {
        val work = Work("sample", "Sample", source, isSample = readOnly)
        val session = EditorSessionViewModel().apply { initialize(listOf(work), work.id) }
        compose.setContent {
            MaterialTheme {
                val value = session.editorValueState.value
                EditorArea(work, work.id, "sketch.js", {}, {}, "sketch.js", "sample/sketch.js",
                    value.text, value, { session.editorValueState.value = it }, { error("Source edit") },
                    false, {}, remember { FocusRequester() }, LocalFocusManager.current, emptyList(), session,
                    FontFamily.Monospace, 14f, false, false, true, "", 0, null, {}, MaterialTheme.colorScheme,
                    { text, _ -> text }, readOnly = readOnly, modifier = Modifier.fillMaxSize())
            }
        }
        compose.waitForIdle()
        if (codeFolds(source).isNotEmpty()) waitForFoldArrows()
        return session
    }

    private fun waitForFoldArrows() {
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodes(hasText("▾", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private val source = "function setup() {\n  createCanvas(300, 300);\n}\nfunction draw() {\n  background(0);\n}\n"

    @Test fun tapVisibleGutterArrowClosesAndReopensSample() {
        val session = showEditor(source)
        compose.waitForIdle()
        val gutter = compose.onNode(hasText("▾1", substring = true), useUnmergedTree = true)
        gutter.performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, 10f)) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(setOf(source.indexOf('{')), session.codeFoldStates["sample/sketch.js"]?.collapsed)
            assertEquals(source, session.editorValueState.value.text)
        }
        compose.onNode(hasText("▸1", substring = true), useUnmergedTree = true)
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, 10f)) }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(session.codeFoldStates["sample/sketch.js"]!!.collapsed.isEmpty()) }
    }
    @Test fun arrowAfterSourceUpdateUsesCurrentSourceAndRegions() {
        val session = showEditor("// Loading", readOnly = false)
        compose.runOnIdle { session.editorValueState.value = TextFieldValue(source) }
        waitForFoldArrows()
        compose.onNode(hasText("▾1", substring = true), useUnmergedTree = true)
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, 10f)) }
        compose.runOnIdle {
            assertEquals(setOf(source.indexOf('{')), session.codeFoldStates["sample/sketch.js"]?.collapsed)
        }
    }

    @Test fun largeSourceCanFoldAfterBackgroundParsingCompletes() {
        val large = source + "// padding\n".repeat(1000)
        val session = showEditor(large)
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodes(hasText("▾", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("▾", substring = true), useUnmergedTree = true)
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, 10f)) }
        compose.runOnIdle {
            assertEquals(setOf(large.indexOf('{')), session.codeFoldStates["sample/sketch.js"]?.collapsed)
            assertEquals(large, session.editorValueState.value.text)
        }
    }

    @Test fun typingBeforeCollapsedBlockRetainsItsCurrentOffsetsAndCaret() {
        val session = showEditor(source, readOnly = false)
        compose.onNode(hasText("▾1", substring = true), useUnmergedTree = true)
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, 10f)) }
        compose.runOnIdle {
            session.editorValueState.value = session.editorValueState.value.copy(selection = androidx.compose.ui.text.TextRange.Zero)
        }
        repeat(8) { step ->
            compose.runOnIdle {
                val before = session.editorValueState.value
                val position = before.selection.start
                session.editorValueState.value = TextFieldValue(before.text.replaceRange(position, position, " "),
                    androidx.compose.ui.text.TextRange(position + 1))
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(setOf(source.indexOf('{') + step + 1), session.codeFoldStates["sample/sketch.js"]!!.collapsed)
                assertEquals(step + 1, session.editorValueState.value.selection.start)
            }
        }
    }

}
