package com.hikariatelier.app

import androidx.compose.foundation.layout.*

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

internal data class ParameterSheetLayout(val isLandscape: Boolean, val editorOnLeft: Boolean,
    val editorWidthFraction: Float, val showStatusBar: Boolean)

@Composable
internal fun LiveParameterSheet(
    work: Work?, editorText: String, sessionViewModel: EditorSessionViewModel,
    workManagementViewModel: WorkManagementViewModel, preview: PreviewController,
    layout: ParameterSheetLayout, textTranslator: (String, Array<out Any?>) -> String,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    fun uiText(source: String, vararg args: Any?) = textTranslator(source, args)
    val parameterWork = work
    val sources = parameterWork?.files.orEmpty().mapValues { (name, code) ->
        sessionViewModel.fileDrafts["${parameterWork?.id}/$name"] ?: code
    } + ("sketch.js" to editorText)
    val declarations = workParameters(sources)
    val scheduleSave = {
        parameterWork?.let { workManagementViewModel.scheduleParameterSave(it.id) }
        Unit
    }
    val flushSaveAndDismiss = {
        parameterWork?.let { workManagementViewModel.flushParameterSave(it.id) }
        onDismiss()
    }
    WorkParameterBottomSheet(
        visible = true,
        declarations = declarations,
        parameterValues = workManagementViewModel.parameterValues(parameterWork),
        isLandscape = layout.isLandscape,
        landscapeEditorOnLeft = layout.editorOnLeft,
        editorWidthFraction = layout.editorWidthFraction,
        showStatusBar = layout.showStatusBar,
        colors = colors,
        onChange = { parameter, value ->
            parameterWork?.let { workManagementViewModel.updateParameter(it.id, parameter.name, value) }
            val jsonValue = when (parameter) {
                is WorkParameter.Number, is WorkParameter.Boolean -> value
                is WorkParameter.Color -> JSONObject.quote(value)
            }
            preview.evaluate(
                "window.__editRinSetParameter?.(${JSONObject.quote(parameter.name)},$jsonValue)"
            )
        },
        onResetParameter = { parameter ->
            parameterWork?.let { workManagementViewModel.updateParameter(it.id, parameter.name, null) }
            val jsonValue = when (parameter) {
                is WorkParameter.Number, is WorkParameter.Boolean -> parameter.defaultValue
                is WorkParameter.Color -> JSONObject.quote(parameter.defaultValue)
            }
            preview.evaluate(
                "window.__editRinSetParameter?.(${JSONObject.quote(parameter.name)},$jsonValue)"
            )
            scheduleSave()
        },
        onResetAll = {
            declarations.forEach { parameter ->
                parameterWork?.let { workManagementViewModel.updateParameter(it.id, parameter.name, null) }
                val jsonValue = when (parameter) {
                    is WorkParameter.Number, is WorkParameter.Boolean -> parameter.defaultValue
                    is WorkParameter.Color -> JSONObject.quote(parameter.defaultValue)
                }
                preview.evaluate(
                    "window.__editRinSetParameter?.(${JSONObject.quote(parameter.name)},$jsonValue)"
                )
            }
            scheduleSave()
        },
        onCommit = scheduleSave,
        onDismiss = flushSaveAndDismiss,
        textTranslator = { s, args -> uiText(s, *args) },
        windowSetup = { KeepLandscapeDialogImmersive(enabled = layout.isLandscape || !layout.showStatusBar) }
    )
}
