package com.hikariatelier.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class AuthoringToolsTest {
    @Test fun prependingKeepsSelectionAndIsOneUndoOperation() {
        val before = TextFieldValue("function draw() {}", TextRange(3, 8))
        val next = prependDeclarations(before, PARAMETER_SAMPLE)
        assertEquals(TextRange(3 + PARAMETER_SAMPLE.length, 8 + PARAMETER_SAMPLE.length), next.selection)
        val session = EditorSessionViewModel()
        session.applyChange(before, next)
        assertEquals(before, session.undo(next))
        assertEquals(next, session.redo(before))
    }
    @Test fun allDialogTypesRoundTripThroughRuntimeParser() {
        ParameterKind.entries.forEach { kind ->
            val initial = when (kind) { ParameterKind.NUMBER -> "1"; ParameterKind.COLOR -> "#123ABC"; ParameterKind.BOOLEAN -> "true" }
            val line = parameterDeclaration(kind, "speed_2", "速さ", initial, "0", "3", "0.1", emptySet(), 0)!!
            val parameter = workParameters(mapOf("sketch.js" to line)).single()
            assertEquals("speed_2", parameter.name)
            assertEquals("速さ", parameter.label)
        }
    }
    @Test fun invalidInputsCannotGenerateIgnoredOrInjectedDeclarations() {
        fun declaration(name: String = "speed", label: String = "Speed", initial: String = "1", min: String = "0",
            max: String = "3", step: String = "0.1", count: Int = 0, existing: Set<String> = emptySet()) =
            parameterDeclaration(ParameterKind.NUMBER, name, label, initial, min, max, step, existing, count)
        listOf("", "1speed", "a b", "x\ny", "constructor", "prototype").forEach { assertNull(declaration(name = it)) }
        listOf("", " ", "\"bad\"", "x\ny", "x\ry", "x".repeat(41)).forEach { assertNull(declaration(label = it)) }
        assertNull(declaration(initial = "NaN")); assertNull(declaration(initial = "Infinity"))
        assertNull(declaration(initial = "1 0.1\nalert(1); //"))
        assertNull(declaration(min = "0 3 1 0.1\nalert(1); //"))
        assertNull(declaration(initial = "4")); assertNull(declaration(min = "3"))
        assertNull(declaration(step = "0")); assertNull(declaration(step = "-1"))
        assertNull(declaration(count = 16)); assertNull(declaration(existing = setOf("speed")))
        assertNull(parameterDeclaration(ParameterKind.COLOR, "ink", "Ink", "#123", "", "", "", emptySet(), 0))
        assertNull(parameterDeclaration(ParameterKind.BOOLEAN, "glow", "Glow", "yes", "", "", "", emptySet(), 0))
    }
    @Test fun diffClassifiesUnicodeMinusAndHeadersBeforePlusMinus() {
        assertEquals(DiffLineKind.ADDED, diffLineKind("+ line"))
        assertEquals(DiffLineKind.REMOVED, diffLineKind("− line"))
        assertEquals(DiffLineKind.REMOVED, diffLineKind("- line"))
        listOf("@@ 1 @@", "+++ after", "--- before", "sketch.js", "helper.js", "shader.frag", "rinParams.speed").forEach {
            assertEquals(DiffLineKind.HEADER, diffLineKind(it))
        }
        assertEquals(DiffLineKind.CONTEXT, diffLineKind(" const x = 1;"))
    }
    @Test fun authoringNamesAndGuidesAreLocalizedInAllSupportedLanguages() {
        val keys = WorkTemplateKind.entries.map { it.title } + ParameterKind.entries.map { it.title } +
            codeSnippets.flatMap { listOf(it.title, it.category, it.placement) } +
            listOf("テンプレート種別", "コードに直接挿入", "＋ パラメータ追加", "PNG画像書き出し", "1x（通常）", "2x（高精細）", "4x（超高精細）", "%s × %s のPNG画像を保存しました")
        keys.forEach { assertTrue("Missing translation: $it", uiTranslations.containsKey(it)) }
        listOf("ja", "en", "zh").forEach {
            assertEquals(5, localizedUserGuide(it).first().steps.size)
        }
    }
}
