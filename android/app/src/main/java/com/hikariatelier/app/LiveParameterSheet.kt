package com.hikariatelier.app

import androidx.compose.foundation.layout.*

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

internal const val USE_INLINE_PARAMETER_PANEL = true

internal data class ParameterSheetLayout(val isLandscape: Boolean, val editorOnLeft: Boolean,
    val editorWidthFraction: Float, val showStatusBar: Boolean)

@Composable
internal fun LiveParameterSheet(
    work: Work?, editorText: String, sessionViewModel: EditorSessionViewModel,
    workManagementViewModel: WorkManagementViewModel, preview: PreviewController,
    layout: ParameterSheetLayout, textTranslator: (String, Array<out Any?>) -> String,
    onDismiss: () -> Unit,
    onJumpToParameter: ((String) -> Unit)? = null
) {
    var showAddParameter by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = MaterialTheme.colorScheme
    fun uiText(source: String, vararg args: Any?) = textTranslator(source, args)
    val parameterWork = work
    val sources = parameterWork?.files.orEmpty().mapValues { (name, code) ->
        sessionViewModel.fileDrafts["${parameterWork?.id}/$name"] ?: code
    } + ("sketch.js" to editorText)
    val declarations = workParameters(sources)
    val scheduleSave = {
        sessionViewModel.commitLiveParameterChange()
        parameterWork?.let { workManagementViewModel.scheduleParameterSave(it.id) }
        Unit
    }
    val flushSaveAndDismiss = {
        sessionViewModel.commitLiveParameterChange()
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
                is WorkParameter.Color, is WorkParameter.Choice -> JSONObject.quote(value)
            }
            preview.evaluate(
                "window.__editRinSetParameter?.(${JSONObject.quote(parameter.name)},$jsonValue)"
            )
            sessionViewModel.applyLiveParameterToEditor(parameter, value)
        },
        onResetParameter = { parameter ->
            parameterWork?.let { workManagementViewModel.updateParameter(it.id, parameter.name, null) }
            val jsonValue = when (parameter) {
                is WorkParameter.Number, is WorkParameter.Boolean -> parameter.defaultValue
                is WorkParameter.Color, is WorkParameter.Choice -> JSONObject.quote(parameter.defaultValue)
            }
            preview.evaluate(
                "window.__editRinSetParameter?.(${JSONObject.quote(parameter.name)},$jsonValue)"
            )
            sessionViewModel.applyLiveParameterToEditor(parameter, parameter.defaultValue)
            scheduleSave()
        },
        onResetAll = {
            declarations.forEach { parameter ->
                parameterWork?.let { workManagementViewModel.updateParameter(it.id, parameter.name, null) }
                val jsonValue = when (parameter) {
                    is WorkParameter.Number, is WorkParameter.Boolean -> parameter.defaultValue
                    is WorkParameter.Color, is WorkParameter.Choice -> JSONObject.quote(parameter.defaultValue)
                }
                preview.evaluate(
                    "window.__editRinSetParameter?.(${JSONObject.quote(parameter.name)},$jsonValue)"
                )
                sessionViewModel.applyLiveParameterToEditor(parameter, parameter.defaultValue)
            }
            scheduleSave()
        },
        onApplyDefaults = {
            if (workManagementViewModel.applyParameterDefaults()) {
                android.widget.Toast.makeText(context, uiText("現在値をコードの初期値に反映しました"), android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        onAddParameter = { showAddParameter = true },
        onInsertSample = {
            if (!workManagementViewModel.insertParameterDeclarations(PARAMETER_SAMPLE)) {
                android.widget.Toast.makeText(context, uiText("パラメータを追加できませんでした"), android.widget.Toast.LENGTH_LONG).show()
            }
        },
        onCommit = scheduleSave,
        onDismiss = flushSaveAndDismiss,
        textTranslator = { s, args -> uiText(s, *args) },
        windowSetup = { KeepLandscapeDialogImmersive() },
        onJumpToParameter = onJumpToParameter,
        onCompareHold = { holding ->
            val targetMap = if (holding) {
                declarations.associate { it.name to it.defaultValue }
            } else {
                workManagementViewModel.parameterValues(parameterWork)
            }
            declarations.forEach { param ->
                val rawVal = if (holding) param.defaultValue else parameterValue(param, targetMap[param.name])
                val jsonValue = when (param) {
                    is WorkParameter.Number, is WorkParameter.Boolean -> rawVal
                    is WorkParameter.Color, is WorkParameter.Choice -> JSONObject.quote(rawVal)
                }
                preview.evaluate("window.__editRinSetParameter?.(${JSONObject.quote(param.name)},$jsonValue)")
            }
        }
    )
    if (showAddParameter) {
        AddParameterDialog(declarations, text = { uiText(it) },
            onAdd = { workManagementViewModel.insertParameterDeclarations(it) },
            onDismiss = { showAddParameter = false })
    }
}

@Composable
internal fun LiveParameterPanel(
    work: Work?,
    editorText: String,
    sessionViewModel: EditorSessionViewModel,
    workManagementViewModel: WorkManagementViewModel,
    preview: PreviewController,
    textTranslator: (String, Array<out Any?>) -> String,
    onDismiss: () -> Unit,
    onJumpToParameter: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAddParameter by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    fun uiText(source: String, vararg args: Any?) = textTranslator(source, args)
    val parameterWork = work
    val sources = parameterWork?.files.orEmpty().mapValues { (name, code) ->
        sessionViewModel.fileDrafts["${parameterWork?.id}/$name"] ?: code
    } + ("sketch.js" to editorText)
    val declarations = workParameters(sources)
    val scheduleSave = {
        sessionViewModel.commitLiveParameterChange()
        parameterWork?.let { workManagementViewModel.scheduleParameterSave(it.id) }
        Unit
    }
    val flushSaveAndDismiss = {
        sessionViewModel.commitLiveParameterChange()
        parameterWork?.let { workManagementViewModel.flushParameterSave(it.id) }
        onDismiss()
    }

    InlineWorkParameterPanel(
        parameters = declarations,
        values = workManagementViewModel.parameterValues(parameterWork),
        onChange = { parameter, value ->
            parameterWork?.let { workManagementViewModel.updateParameter(it.id, parameter.name, value) }
            val jsonValue = when (parameter) {
                is WorkParameter.Number, is WorkParameter.Boolean -> value
                is WorkParameter.Color, is WorkParameter.Choice -> JSONObject.quote(value)
            }
            preview.evaluate(
                "window.__editRinSetParameter?.(${JSONObject.quote(parameter.name)},$jsonValue)"
            )
            sessionViewModel.applyLiveParameterToEditor(parameter, value)
        },
        onResetParameter = { parameter ->
            parameterWork?.let { workManagementViewModel.updateParameter(it.id, parameter.name, null) }
            val jsonValue = when (parameter) {
                is WorkParameter.Number, is WorkParameter.Boolean -> parameter.defaultValue
                is WorkParameter.Color, is WorkParameter.Choice -> JSONObject.quote(parameter.defaultValue)
            }
            preview.evaluate(
                "window.__editRinSetParameter?.(${JSONObject.quote(parameter.name)},$jsonValue)"
            )
            sessionViewModel.applyLiveParameterToEditor(parameter, parameter.defaultValue)
            scheduleSave()
        },
        onResetAll = {
            declarations.forEach { parameter ->
                parameterWork?.let { workManagementViewModel.updateParameter(it.id, parameter.name, null) }
                val jsonValue = when (parameter) {
                    is WorkParameter.Number, is WorkParameter.Boolean -> parameter.defaultValue
                    is WorkParameter.Color, is WorkParameter.Choice -> JSONObject.quote(parameter.defaultValue)
                }
                preview.evaluate(
                    "window.__editRinSetParameter?.(${JSONObject.quote(parameter.name)},$jsonValue)"
                )
                sessionViewModel.applyLiveParameterToEditor(parameter, parameter.defaultValue)
            }
            scheduleSave()
        },
        onApplyDefaults = {
            if (workManagementViewModel.applyParameterDefaults()) {
                android.widget.Toast.makeText(context, uiText("現在値をコードの初期値に反映しました"), android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        onAddParameter = { showAddParameter = true },
        onInsertSample = {
            if (!workManagementViewModel.insertParameterDeclarations(PARAMETER_SAMPLE)) {
                android.widget.Toast.makeText(context, uiText("パラメータを追加できませんでした"), android.widget.Toast.LENGTH_LONG).show()
            }
        },
        onCommit = scheduleSave,
        onDismiss = flushSaveAndDismiss,
        text = { s -> uiText(s) },
        onJumpToParameter = onJumpToParameter,
        modifier = modifier,
        onCompareHold = { holding ->
            val targetMap = if (holding) {
                declarations.associate { it.name to it.defaultValue }
            } else {
                workManagementViewModel.parameterValues(parameterWork)
            }
            declarations.forEach { param ->
                val rawVal = if (holding) param.defaultValue else parameterValue(param, targetMap[param.name])
                val jsonValue = when (param) {
                    is WorkParameter.Number, is WorkParameter.Boolean -> rawVal
                    is WorkParameter.Color, is WorkParameter.Choice -> JSONObject.quote(rawVal)
                }
                preview.evaluate("window.__editRinSetParameter?.(${JSONObject.quote(param.name)},$jsonValue)")
            }
        }
    )

    if (showAddParameter) {
        AddParameterDialog(declarations, text = { uiText(it) },
            onAdd = { workManagementViewModel.insertParameterDeclarations(it) },
            onDismiss = { showAddParameter = false })
    }
}
