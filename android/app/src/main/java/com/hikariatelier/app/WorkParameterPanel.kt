package com.hikariatelier.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

internal fun formatNumberValue(v: Float, step: Float): String {
    val stepStr = step.toString()
    val decimals = if (stepStr.contains('.')) {
        val frac = stepStr.substringAfter('.').trimEnd('0')
        frac.length.coerceIn(0, 4)
    } else 0
    return if (decimals == 0) {
        v.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.${decimals}f", v)
    }
}

@Composable
internal fun WorkParameterPanel(
    parameters: List<WorkParameter>,
    values: Map<String, String>,
    onChange: (WorkParameter, String) -> Unit,
    onCommit: () -> Unit,
    text: (String) -> String,
    onResetParameter: ((WorkParameter) -> Unit)? = null,
    onResetAll: (() -> Unit)? = null,
    onApplyDefaults: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onAddParameter: (() -> Unit)? = null,
    onInsertSample: (() -> Unit)? = null,
    onJumpToParameter: ((String) -> Unit)? = null,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onCompareHold: ((Boolean) -> Unit)? = null,
    initialDefaults: Map<String, String>? = null,
    presets: List<WorkParameterPreset> = emptyList(),
    onSavePreset: ((String, Map<String, String>, String?) -> Boolean)? = null,
    onRenamePreset: ((String, String) -> Boolean)? = null,
    onDeletePreset: ((String) -> Boolean)? = null,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var morphJob by remember { mutableStateOf<Job?>(null) }
    var morphEnabled by rememberSaveable { mutableStateOf(true) }
    var showBlendBar by rememberSaveable { mutableStateOf(false) }
    var blendPresetAId by rememberSaveable { mutableStateOf<String?>(null) }
    var blendPresetBId by rememberSaveable { mutableStateOf<String?>(null) }
    var blendFraction by remember { mutableFloatStateOf(0f) }
    var showSavePresetDialog by rememberSaveable { mutableStateOf(false) }
    var renamingPresetId by rememberSaveable { mutableStateOf<String?>(null) }
    var lockedParameters by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val compareInteractionSource = remember { MutableInteractionSource() }
    val isComparing by compareInteractionSource.collectIsPressedAsState()
    var comparisonSnapshot by remember { mutableStateOf<Map<String, String>?>(null) }

    val latestParameters by rememberUpdatedState(parameters)
    val latestValues by rememberUpdatedState(values)
    val latestLocked by rememberUpdatedState(lockedParameters)
    val latestOnChange by rememberUpdatedState(onChange)
    val latestOnCommit by rememberUpdatedState(onCommit)
    val latestOnResetParameter by rememberUpdatedState(onResetParameter)
    val latestOnJumpToParameter by rememberUpdatedState(onJumpToParameter)
    val latestText by rememberUpdatedState(text)

    val cancelMorph: () -> Unit = remember {
        {
            morphJob?.cancel()
            morphJob = null
        }
    }
    val emitManualChange: (WorkParameter, String) -> Unit = remember {
        { param, nextVal ->
            cancelMorph()
            val currentVal = latestValues[param.name]
            val same = currentVal == nextVal || (currentVal == null && param.defaultValue == nextVal)
            if (!same) {
                latestOnChange(param, nextVal)
            }
        }
    }
    val stableOnCommit: () -> Unit = remember { { latestOnCommit() } }
    val stableOnToggleLock: (String) -> Unit = remember {
        { name ->
            lockedParameters = if (name in latestLocked) {
                latestLocked - name
            } else {
                latestLocked + name
            }
        }
    }
    val stableOnResetParameter: ((WorkParameter) -> Unit)? = if (onResetParameter != null) {
        remember {
            { param ->
                cancelMorph()
                latestOnResetParameter?.invoke(param)
            }
        }
    } else null
    val stableOnJumpToParameter: ((String) -> Unit)? = if (onJumpToParameter != null) {
        remember { { name -> latestOnJumpToParameter?.invoke(name) } }
    } else null
    val stableText: (String) -> String = remember { { s -> latestText(s) } }

    DisposableEffect(Unit) {
        onDispose { morphJob?.cancel() }
    }

    LaunchedEffect(isComparing) {
        if (parameters.isEmpty()) return@LaunchedEffect
        if (isComparing) cancelMorph()
        if (onCompareHold != null) {
            onCompareHold(isComparing)
        } else {
            if (isComparing) {
                comparisonSnapshot = values.toMap()
                parameters.forEach { p ->
                    val initialDef = initialDefaults?.get(p.name) ?: p.defaultValue
                    onChange(p, initialDef)
                }
            } else {
                val snapshot = comparisonSnapshot
                if (snapshot != null) {
                    parameters.forEach { p ->
                        val saved = snapshot[p.name]
                        if (saved != null) {
                            onChange(p, saved)
                        }
                    }
                    comparisonSnapshot = null
                }
            }
        }
    }
    val hasModified = remember(parameters, values, initialDefaults) {
        parameters.any { p ->
            val defValue = initialDefaults?.get(p.name) ?: p.defaultValue
            isParameterModified(p, values[p.name], defValue)
        }
    }

    fun applyPreset(preset: WorkParameterPreset) {
        cancelMorph()
        val params = latestParameters
        if (params.isEmpty()) return
        val startValues = params.associate { it.name to parameterValue(it, latestValues[it.name]) }
        val targetValues = params.associate { it.name to parameterValue(it, preset.values[it.name]) }
        val locked = latestLocked
        if (!morphEnabled) {
            var changedAny = false
            params.forEach { param ->
                if (param.name !in locked) {
                    val target = targetValues[param.name] ?: param.defaultValue
                    if (isParameterModified(param, startValues[param.name], target)) {
                        latestOnChange(param, target)
                        changedAny = true
                    }
                }
            }
            if (changedAny) latestOnCommit()
            return
        }
        morphJob = coroutineScope.launch {
            val durationNanos = 650_000_000L
            val startNanos = withFrameNanos { it }
            val lastEmitted = startValues.toMutableMap()
            while (true) {
                val now = withFrameNanos { it }
                val progress = ((now - startNanos).toFloat() / durationNanos.toFloat()).coerceIn(0f, 1f)
                val eased = smoothstep(progress)
                val frameValues = interpolateWorkParameters(params, startValues, targetValues, eased, locked)
                params.forEach { param ->
                    val nextVal = frameValues[param.name] ?: return@forEach
                    if (lastEmitted[param.name] != nextVal) {
                        lastEmitted[param.name] = nextVal
                        latestOnChange(param, nextVal)
                    }
                }
                if (progress >= 1f) break
            }
            latestOnCommit()
            morphJob = null
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = topPadding, bottom = if (isFullscreen) 180.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text("パラメータ"), style = MaterialTheme.typography.titleLarge)
                if (parameters.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${parameters.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                cancelMorph()
                                val randomMap = randomizeWorkParameters(parameters, values, lockedParameters)
                                randomMap.forEach { (name, value) ->
                                    val param = parameters.find { it.name == name }
                                    if (param != null && values[name] != value) {
                                        onChange(param, value)
                                    }
                                }
                                onCommit()
                            },
                            shape = UNIT_CONTROL_SHAPE,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_dice),
                                contentDescription = text("パラメータをランダムに変更"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {},
                            interactionSource = compareInteractionSource,
                            shape = UNIT_CONTROL_SHAPE,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_compare),
                                contentDescription = text("押している間、初期値と比較"),
                                tint = if (isComparing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                onToggleFullscreen?.let { toggleFullscreen ->
                    IconButton(
                        onClick = toggleFullscreen,
                        shape = UNIT_CONTROL_SHAPE,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isFullscreen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (isFullscreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pip_expand),
                            contentDescription = text(if (isFullscreen) "分割表示に戻す" else "全画面（PiPプレビュー）"),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                onDismiss?.let { dismiss ->
                    IconButton(onClick = dismiss, shape = UNIT_CONTROL_SHAPE) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = text("閉じる")
                        )
                    }
                }
            }
        }

        if (hasModified && (onApplyDefaults != null || onResetAll != null)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onApplyDefaults != null) {
                    TextButton(
                        onClick = onApplyDefaults,
                        shape = UNIT_CONTROL_SHAPE,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_save),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text("現在値を初期値にする"),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (onResetAll != null) {
                    TextButton(
                        onClick = {
                            cancelMorph()
                            onResetAll()
                        },
                        shape = UNIT_CONTROL_SHAPE,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_restore),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text("初期値に戻す"),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        onAddParameter?.let { add ->
            OutlinedButton(
                onClick = add,
                enabled = parameters.size < 16,
                shape = UNIT_CONTROL_SHAPE,
                border = BorderStroke(UNIT_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text("＋ パラメータ追加"))
            }
        }

        // Parameter presets & morphing section
        if (parameters.isNotEmpty() && (onSavePreset != null || presets.isNotEmpty())) {
            val effectiveCurrentValues = remember(parameters, values) {
                parameters.associate { it.name to parameterValue(it, values[it.name]) }
            }
            val activePresetId = remember(parameters, effectiveCurrentValues, presets) {
                presets.firstOrNull { preset ->
                    parameterValuesMatch(parameters, effectiveCurrentValues, preset.values)
                }?.id
            }
            val resolvedA = presets.firstOrNull { it.id == blendPresetAId } ?: presets.getOrNull(0)
            val resolvedB = presets.firstOrNull { it.id == blendPresetBId && it.id != resolvedA?.id }
                ?: presets.firstOrNull { it.id != resolvedA?.id }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = UNIT_CARD_SHAPE,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(UNIT_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = text("プリセット"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${presets.size}/$MAX_PARAMETER_PRESETS",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (presets.isNotEmpty()) {
                                FilterChip(
                                    selected = morphEnabled,
                                    onClick = { morphEnabled = !morphEnabled },
                                    shape = UNIT_CONTROL_SHAPE,
                                    colors = unitFilterChipColors(),
                                    border = unitFilterChipBorder(morphEnabled),
                                    label = {
                                        Text(
                                            text = text("補間"),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.height(30.dp)
                                )
                            }
                            if (presets.size >= 2) {
                                FilterChip(
                                    selected = showBlendBar,
                                    onClick = {
                                        showBlendBar = !showBlendBar
                                        if (showBlendBar && resolvedA != null && resolvedB != null) {
                                            blendPresetAId = resolvedA.id
                                            blendPresetBId = resolvedB.id
                                            blendFraction = 0f
                                        }
                                    },
                                    shape = UNIT_CONTROL_SHAPE,
                                    colors = unitFilterChipColors(),
                                    border = unitFilterChipBorder(showBlendBar),
                                    label = {
                                        Text(
                                            text = "A↔B",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    },
                                    modifier = Modifier.height(30.dp)
                                )
                            }
                            if (onSavePreset != null) {
                                FilledTonalButton(
                                    onClick = { showSavePresetDialog = true },
                                    enabled = presets.size < MAX_PARAMETER_PRESETS,
                                    shape = UNIT_CONTROL_SHAPE,
                                    colors = unitTonalButtonColors(),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_add),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = text("保存"),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    if (presets.isEmpty()) {
                        Text(
                            text = text("気に入ったパラメータの組み合わせを保存し、ワンタップで切り替えや補間ができます"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            presets.forEach { preset ->
                                key(preset.id) {
                                    val isActive = preset.id == activePresetId
                                    var menuOpen by remember(preset.id) { mutableStateOf(false) }
                                    val presetColors = remember(preset.values, parameters) {
                                        parameters.filterIsInstance<WorkParameter.Color>()
                                            .mapNotNull { cp -> parseHexColor(preset.values[cp.name] ?: cp.defaultValue) }
                                            .take(3)
                                    }
                                    Box {
                                        Surface(
                                            shape = UNIT_CONTROL_SHAPE,
                                            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(
                                                UNIT_BORDER_WIDTH,
                                                if (isActive) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outlineVariant
                                            )
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(UNIT_CONTROL_SHAPE)
                                                    .clickable { applyPreset(preset) }
                                                    .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
                                            ) {
                                                if (presetColors.isNotEmpty()) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(end = 6.dp)
                                                    ) {
                                                        presetColors.forEach { swatch ->
                                                            Box(
                                                                Modifier
                                                                    .size(width = 4.dp, height = 12.dp)
                                                                    .clip(RoundedCornerShape(2.dp))
                                                                    .background(swatch)
                                                            )
                                                        }
                                                    }
                                                } else if (isActive) {
                                                    Box(
                                                        Modifier
                                                            .padding(end = 6.dp)
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary)
                                                    )
                                                }
                                                Text(
                                                    text = preset.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isActive) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 128.dp)
                                                )
                                                if (onSavePreset != null || onRenamePreset != null || onDeletePreset != null) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(UNIT_COMPACT_CONTROL_SHAPE)
                                                            .clickable { menuOpen = true },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.ic_more_vertical),
                                                            contentDescription = text("プリセット操作"),
                                                            tint = if (isActive) MaterialTheme.colorScheme.primary
                                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                } else {
                                                    Spacer(Modifier.width(8.dp))
                                                }
                                            }
                                        }

                                        UnitDropdownMenu(
                                            expanded = menuOpen,
                                            onDismissRequest = { menuOpen = false }
                                        ) {
                                            if (onSavePreset != null) {
                                                DropdownMenuItem(
                                                    text = { Text(text("現在の値で上書き")) },
                                                    leadingIcon = {
                                                        Icon(
                                                            painter = painterResource(R.drawable.ic_save),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    },
                                                    onClick = {
                                                        menuOpen = false
                                                        if (onSavePreset(preset.name, effectiveCurrentValues, preset.id)) {
                                                            Toast.makeText(context, text("プリセットを更新しました"), Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                )
                                            }
                                            if (onRenamePreset != null) {
                                                DropdownMenuItem(
                                                    text = { Text(text("名前を変更")) },
                                                    leadingIcon = {
                                                        Icon(
                                                            painter = painterResource(R.drawable.ic_rename),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    },
                                                    onClick = {
                                                        menuOpen = false
                                                        renamingPresetId = preset.id
                                                    }
                                                )
                                            }
                                            if (onDeletePreset != null) {
                                                DropdownMenuItem(
                                                    text = { Text(text("削除"), color = MaterialTheme.colorScheme.error) },
                                                    leadingIcon = {
                                                        Icon(
                                                            painter = painterResource(R.drawable.ic_delete),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    },
                                                    onClick = {
                                                        menuOpen = false
                                                        onDeletePreset(preset.id)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showBlendBar && resolvedA != null && resolvedB != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PresetSelectorChip(
                                        prefix = "A",
                                        selected = resolvedA,
                                        presets = presets,
                                        onSelect = { blendPresetAId = it.id }
                                    )
                                    Text(
                                        text = "${(blendFraction * 100f).roundToInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    PresetSelectorChip(
                                        prefix = "B",
                                        selected = resolvedB,
                                        presets = presets,
                                        onSelect = { blendPresetBId = it.id }
                                    )
                                }
                                StudioSlider(
                                    value = blendFraction,
                                    onValueChange = { fraction ->
                                        cancelMorph()
                                        blendFraction = fraction
                                        val blended = interpolateWorkParameters(
                                            parameters, resolvedA.values, resolvedB.values, fraction, lockedParameters
                                        )
                                        parameters.forEach { param ->
                                            val nextVal = blended[param.name] ?: return@forEach
                                            if (values[param.name] != nextVal) {
                                                onChange(param, nextVal)
                                            }
                                        }
                                    },
                                    onValueChangeFinished = onCommit,
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Empty state
        if (parameters.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = UNIT_CARD_SHAPE,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(UNIT_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text("コードにパラメータ宣言を追加してください"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text("コードでは rinParams.speed と rinParams.ink を使います"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = UNIT_CONTROL_SHAPE,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(
                            UNIT_BORDER_WIDTH,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = UNIT_HAIRLINE_ALPHA)
                        )
                    ) {
                        Text(
                            text = PARAMETER_SAMPLE.trimEnd(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    onInsertSample?.let { insert ->
                        Button(onClick = insert, shape = UNIT_CONTROL_SHAPE, modifier = Modifier.fillMaxWidth()) {
                            Text(text("コードに直接挿入"))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("rinParams", PARAMETER_SAMPLE))
                            Toast.makeText(context, text("テンプレートをコピーしました"), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = UNIT_CONTROL_SHAPE
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_duplicate),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text("テンプレートをコピー"))
                    }
                }
            }
        }

        // Parameter items
        parameters.forEach { parameter ->
            key(parameter.name) {
                val value = parameterValue(parameter, values[parameter.name])
                val defValue = initialDefaults?.get(parameter.name) ?: parameter.defaultValue
                val isModified = isParameterModified(parameter, value, defValue)
                val isLocked = parameter.name in lockedParameters

                ParameterItemCard(
                    parameter = parameter,
                    value = value,
                    defValue = defValue,
                    isModified = isModified,
                    isLocked = isLocked,
                    onToggleLock = stableOnToggleLock,
                    onJumpToParameter = stableOnJumpToParameter,
                    onEmitManualChange = emitManualChange,
                    onCommit = stableOnCommit,
                    onResetParameter = stableOnResetParameter,
                    text = stableText
                )
            }
        }
    }

    if (showSavePresetDialog && onSavePreset != null) {
        val suggestedName = remember(presets) {
            val existingNames = presets.map { it.name }.toSet()
            var index = presets.size + 1
            while ("Look $index" in existingNames) index++
            "Look $index"
        }
        ParameterPresetNameDialog(
            title = text("プリセットを保存"),
            initialName = suggestedName,
            confirmLabel = text("保存"),
            text = text,
            onConfirm = { enteredName ->
                val snapshot = parameters.associate { it.name to parameterValue(it, values[it.name]) }
                if (onSavePreset(enteredName, snapshot, null)) {
                    Toast.makeText(context, text("プリセットを保存しました"), Toast.LENGTH_SHORT).show()
                    showSavePresetDialog = false
                }
            },
            onDismiss = { showSavePresetDialog = false }
        )
    }

    val renamingPreset = presets.firstOrNull { it.id == renamingPresetId }
    if (renamingPreset != null && onRenamePreset != null) {
        ParameterPresetNameDialog(
            title = text("プリセット名を変更"),
            initialName = renamingPreset.name,
            confirmLabel = text("変更"),
            text = text,
            onConfirm = { enteredName ->
                if (onRenamePreset(renamingPreset.id, enteredName)) {
                    renamingPresetId = null
                }
            },
            onDismiss = { renamingPresetId = null }
        )
    }
}

@Composable
private fun RowScope.ParameterLabelHeader(
    parameter: WorkParameter,
    isLocked: Boolean,
    onToggleLock: (String) -> Unit,
    onJumpToParameter: ((String) -> Unit)?,
    text: (String) -> String
) {
    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = { onToggleLock(parameter.name) },
            shape = UNIT_COMPACT_CONTROL_SHAPE,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                painter = painterResource(if (isLocked) R.drawable.ic_pin_filled else R.drawable.ic_pin),
                contentDescription = text(if (isLocked) "パラメータの固定を解除" else "パラメータを固定"),
                modifier = Modifier.size(16.dp),
                tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onJumpToParameter != null) {
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onJumpToParameter(parameter.name) }
                    } else Modifier
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = parameter.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (onJumpToParameter != null) {
                    Text(
                        text = "↗",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = "rinParams.${parameter.name}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ParameterItemCard(
    parameter: WorkParameter,
    value: String,
    defValue: String,
    isModified: Boolean,
    isLocked: Boolean,
    onToggleLock: (String) -> Unit,
    onJumpToParameter: ((String) -> Unit)?,
    onEmitManualChange: (WorkParameter, String) -> Unit,
    onCommit: () -> Unit,
    onResetParameter: ((WorkParameter) -> Unit)?,
    text: (String) -> String
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = UNIT_CARD_SHAPE,
        color = colors.surface,
        border = BorderStroke(
            UNIT_BORDER_WIDTH,
            if (isLocked) colors.primary.copy(alpha = 0.65f) else colors.outlineVariant
        ),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isModified) {
                        Modifier
                            .clip(UNIT_CARD_SHAPE)
                            .drawBehind {
                                drawRect(
                                    color = colors.primary,
                                    topLeft = Offset.Zero,
                                    size = Size(3.dp.toPx(), size.height)
                                )
                            }
                    } else Modifier
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (parameter) {
                is WorkParameter.Number -> NumberParameterCard(
                    parameter = parameter,
                    value = value,
                    defValue = defValue,
                    isModified = isModified,
                    isLocked = isLocked,
                    onToggleLock = onToggleLock,
                    onJumpToParameter = onJumpToParameter,
                    onEmitManualChange = onEmitManualChange,
                    onCommit = onCommit,
                    onResetParameter = onResetParameter,
                    text = text
                )
                is WorkParameter.Boolean -> BooleanParameterCard(
                    parameter = parameter,
                    value = value,
                    isModified = isModified,
                    isLocked = isLocked,
                    onToggleLock = onToggleLock,
                    onJumpToParameter = onJumpToParameter,
                    onEmitManualChange = onEmitManualChange,
                    onCommit = onCommit,
                    onResetParameter = onResetParameter,
                    text = text
                )
                is WorkParameter.Color -> ColorParameterCard(
                    parameter = parameter,
                    value = value,
                    isModified = isModified,
                    isLocked = isLocked,
                    onToggleLock = onToggleLock,
                    onJumpToParameter = onJumpToParameter,
                    onEmitManualChange = onEmitManualChange,
                    onCommit = onCommit,
                    onResetParameter = onResetParameter,
                    text = text
                )
                is WorkParameter.Choice -> ChoiceParameterCard(
                    parameter = parameter,
                    value = value,
                    isModified = isModified,
                    isLocked = isLocked,
                    onToggleLock = onToggleLock,
                    onJumpToParameter = onJumpToParameter,
                    onEmitManualChange = onEmitManualChange,
                    onCommit = onCommit,
                    onResetParameter = onResetParameter,
                    text = text
                )
                is WorkParameter.Point2D -> Point2DParameterCard(
                    parameter = parameter,
                    value = value,
                    defValue = defValue,
                    isModified = isModified,
                    isLocked = isLocked,
                    onToggleLock = onToggleLock,
                    onJumpToParameter = onJumpToParameter,
                    onEmitManualChange = onEmitManualChange,
                    onCommit = onCommit,
                    onResetParameter = onResetParameter,
                    text = text
                )
            }
        }
    }
}

@Composable
private fun NumberParameterCard(
    parameter: WorkParameter.Number,
    value: String,
    defValue: String,
    isModified: Boolean,
    isLocked: Boolean,
    onToggleLock: (String) -> Unit,
    onJumpToParameter: ((String) -> Unit)?,
    onEmitManualChange: (WorkParameter, String) -> Unit,
    onCommit: () -> Unit,
    onResetParameter: ((WorkParameter) -> Unit)?,
    text: (String) -> String
) {
    val numeric = value.toFloatOrNull() ?: parameter.defaultValue.toFloatOrNull() ?: 0f
    val formattedVal = formatNumberValue(numeric, parameter.step)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ParameterLabelHeader(
            parameter = parameter,
            isLocked = isLocked,
            onToggleLock = onToggleLock,
            onJumpToParameter = onJumpToParameter,
            text = text
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = UNIT_COMPACT_CONTROL_SHAPE,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = formattedVal,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            if (isModified && onResetParameter != null) {
                IconButton(
                    onClick = { onResetParameter(parameter) },
                    shape = UNIT_COMPACT_CONTROL_SHAPE,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_restore),
                        contentDescription = text("初期値に戻す"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedButton(
            onClick = {
                val nextVal = (numeric - parameter.step).coerceIn(parameter.min, parameter.max)
                val nextStr = formatNumberValue(nextVal, parameter.step)
                onEmitManualChange(parameter, nextStr)
                onCommit()
            },
            shape = UNIT_COMPACT_CONTROL_SHAPE,
            border = BorderStroke(UNIT_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 32.dp),
            enabled = numeric > parameter.min
        ) {
            Text("−", fontWeight = FontWeight.Bold)
        }

        StudioSlider(
            value = numeric,
            onValueChange = { raw ->
                val steps = ((raw - parameter.min) / parameter.step).roundToInt()
                val snapped = (parameter.min + steps * parameter.step)
                    .coerceIn(parameter.min, parameter.max)
                onEmitManualChange(parameter, formatNumberValue(snapped, parameter.step))
            },
            onValueChangeFinished = onCommit,
            valueRange = parameter.min..parameter.max,
            defaultValue = defValue.toFloatOrNull(),
            modifier = Modifier.weight(1f)
        )

        OutlinedButton(
            onClick = {
                val nextVal = (numeric + parameter.step).coerceIn(parameter.min, parameter.max)
                val nextStr = formatNumberValue(nextVal, parameter.step)
                onEmitManualChange(parameter, nextStr)
                onCommit()
            },
            shape = UNIT_COMPACT_CONTROL_SHAPE,
            border = BorderStroke(UNIT_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 32.dp),
            enabled = numeric < parameter.max
        ) {
            Text("+", fontWeight = FontWeight.Bold)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${formatNumberValue(parameter.min, parameter.step)} .. ${formatNumberValue(parameter.max, parameter.step)} (step: ${formatNumberValue(parameter.step, parameter.step)})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isModified) {
            Text(
                text = "${text("初期値")}: $defValue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BooleanParameterCard(
    parameter: WorkParameter.Boolean,
    value: String,
    isModified: Boolean,
    isLocked: Boolean,
    onToggleLock: (String) -> Unit,
    onJumpToParameter: ((String) -> Unit)?,
    onEmitManualChange: (WorkParameter, String) -> Unit,
    onCommit: () -> Unit,
    onResetParameter: ((WorkParameter) -> Unit)?,
    text: (String) -> String
) {
    val boolVal = value.toBoolean()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ParameterLabelHeader(
            parameter = parameter,
            isLocked = isLocked,
            onToggleLock = onToggleLock,
            onJumpToParameter = onJumpToParameter,
            text = text
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = UNIT_COMPACT_CONTROL_SHAPE,
                color = if (boolVal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = if (boolVal) "ON" else "OFF",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (boolVal) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Switch(
                checked = boolVal,
                onCheckedChange = { checked ->
                    onEmitManualChange(parameter, checked.toString())
                    onCommit()
                },
                colors = unitSwitchColors()
            )

            if (isModified && onResetParameter != null) {
                IconButton(
                    onClick = { onResetParameter(parameter) },
                    shape = UNIT_COMPACT_CONTROL_SHAPE,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_restore),
                        contentDescription = text("初期値に戻す"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorParameterCard(
    parameter: WorkParameter.Color,
    value: String,
    isModified: Boolean,
    isLocked: Boolean,
    onToggleLock: (String) -> Unit,
    onJumpToParameter: ((String) -> Unit)?,
    onEmitManualChange: (WorkParameter, String) -> Unit,
    onCommit: () -> Unit,
    onResetParameter: ((WorkParameter) -> Unit)?,
    text: (String) -> String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ParameterLabelHeader(
            parameter = parameter,
            isLocked = isLocked,
            onToggleLock = onToggleLock,
            onJumpToParameter = onJumpToParameter,
            text = text
        )

        if (isModified && onResetParameter != null) {
            IconButton(
                onClick = { onResetParameter(parameter) },
                shape = UNIT_COMPACT_CONTROL_SHAPE,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_restore),
                    contentDescription = text("初期値に戻す"),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    CustomColorSetting(
        label = "",
        value = (value.removePrefix("#").toIntOrNull(16) ?: 0xFFFFFF) or 0xFF000000.toInt(),
        onChange = { color ->
            onEmitManualChange(parameter, "#%06X".format(color and 0xFFFFFF))
            onCommit()
        },
        text = text
    )
}

@Composable
private fun ChoiceParameterCard(
    parameter: WorkParameter.Choice,
    value: String,
    isModified: Boolean,
    isLocked: Boolean,
    onToggleLock: (String) -> Unit,
    onJumpToParameter: ((String) -> Unit)?,
    onEmitManualChange: (WorkParameter, String) -> Unit,
    onCommit: () -> Unit,
    onResetParameter: ((WorkParameter) -> Unit)?,
    text: (String) -> String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ParameterLabelHeader(
            parameter = parameter,
            isLocked = isLocked,
            onToggleLock = onToggleLock,
            onJumpToParameter = onJumpToParameter,
            text = text
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = UNIT_COMPACT_CONTROL_SHAPE,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            if (isModified && onResetParameter != null) {
                IconButton(
                    onClick = { onResetParameter(parameter) },
                    shape = UNIT_COMPACT_CONTROL_SHAPE,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_restore),
                        contentDescription = text("初期値に戻す"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    val selectedIdx = parameter.options.indexOf(value).let { if (it >= 0) it else 0 }
    ScrollableChoiceGroup(
        labels = parameter.options,
        selectedIndex = selectedIdx,
        onSelect = { idx ->
            if (idx in parameter.options.indices) {
                onEmitManualChange(parameter, parameter.options[idx])
                onCommit()
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    if (isModified) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "${text("初期値")}: ${parameter.defaultValue}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Point2DParameterCard(
    parameter: WorkParameter.Point2D,
    value: String,
    defValue: String,
    isModified: Boolean,
    isLocked: Boolean,
    onToggleLock: (String) -> Unit,
    onJumpToParameter: ((String) -> Unit)?,
    onEmitManualChange: (WorkParameter, String) -> Unit,
    onCommit: () -> Unit,
    onResetParameter: ((WorkParameter) -> Unit)?,
    text: (String) -> String
) {
    val (curX, curY) = parsePoint2DValue(value)
        ?: parsePoint2DValue(parameter.defaultValue)
        ?: (parameter.minX to parameter.minY)
    val fx = formatNumberValue(curX, parameter.stepX)
    val fy = formatNumberValue(curY, parameter.stepY)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ParameterLabelHeader(
            parameter = parameter,
            isLocked = isLocked,
            onToggleLock = onToggleLock,
            onJumpToParameter = onJumpToParameter,
            text = text
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = UNIT_COMPACT_CONTROL_SHAPE,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "X:$fx  Y:$fy",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (isModified && onResetParameter != null) {
                IconButton(
                    onClick = { onResetParameter(parameter) },
                    shape = UNIT_COMPACT_CONTROL_SHAPE,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_restore),
                        contentDescription = text("初期値に戻す"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    val defaultPair = parsePoint2DValue(defValue)
    ParameterXYPad(
        parameter = parameter,
        valueX = curX,
        valueY = curY,
        defaultX = defaultPair?.first,
        defaultY = defaultPair?.second,
        onValueChange = { nextX, nextY ->
            onEmitManualChange(
                parameter,
                formatPoint2DValue(nextX, nextY, parameter.stepX, parameter.stepY)
            )
        },
        onCommit = onCommit
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "X:${formatNumberValue(parameter.minX, parameter.stepX)}..${formatNumberValue(parameter.maxX, parameter.stepX)}  Y:${formatNumberValue(parameter.minY, parameter.stepY)}..${formatNumberValue(parameter.maxY, parameter.stepY)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isModified) {
            Text(
                text = "${text("初期値")}: $defValue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PresetSelectorChip(
    prefix: String,
    selected: WorkParameterPreset,
    presets: List<WorkParameterPreset>,
    onSelect: (WorkParameterPreset) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = UNIT_CONTROL_SHAPE,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(UNIT_CONTROL_SHAPE)
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$prefix:",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = selected.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 90.dp)
                )
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        UnitDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        onSelect(preset)
                    }
                )
            }
        }
    }
}

@Composable
private fun ParameterPresetNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    text: (String) -> String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val trimmed = name.trim()
    EditSettingsDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= MAX_PARAMETER_PRESET_NAME) name = it },
                label = { Text(text("プリセット名")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = trimmed.isNotEmpty(),
                onClick = { onConfirm(trimmed) }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text("キャンセル")) }
        }
    )
}

@Composable
private fun ParameterXYPad(
    parameter: WorkParameter.Point2D,
    valueX: Float,
    valueY: Float,
    defaultX: Float? = null,
    defaultY: Float? = null,
    onValueChange: (Float, Float) -> Unit,
    onCommit: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val latestParam by rememberUpdatedState(parameter)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnCommit by rememberUpdatedState(onCommit)

    val normX = if (parameter.maxX > parameter.minX) ((valueX - parameter.minX) / (parameter.maxX - parameter.minX)).coerceIn(0f, 1f) else 0.5f
    val normY = if (parameter.maxY > parameter.minY) ((valueY - parameter.minY) / (parameter.maxY - parameter.minY)).coerceIn(0f, 1f) else 0.5f
    val defaultNormX = if (defaultX != null && parameter.maxX > parameter.minX) {
        ((defaultX - parameter.minX) / (parameter.maxX - parameter.minX)).coerceIn(0f, 1f)
    } else null
    val defaultNormY = if (defaultY != null && parameter.maxY > parameter.minY) {
        ((defaultY - parameter.minY) / (parameter.maxY - parameter.minY)).coerceIn(0f, 1f)
    } else null

    var isDragging by remember { mutableStateOf(false) }
    val haloRadius by animateDpAsState(
        targetValue = if (isDragging) 22.dp else 15.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "xyHalo"
    )

    val padShape = UNIT_CONTROL_SHAPE
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(padShape)
            .background(colors.surfaceContainerLowest)
            .border(UNIT_BORDER_WIDTH, colors.outlineVariant, padShape)
            .pointerInput(parameter.name) {
                var lastEmittedX = Float.NaN
                var lastEmittedY = Float.NaN
                fun emitForPosition(pos: Offset) {
                    val p = latestParam
                    val w = size.width.coerceAtLeast(1).toFloat()
                    val h = size.height.coerceAtLeast(1).toFloat()
                    val nx = (pos.x / w).coerceIn(0f, 1f)
                    val ny = (pos.y / h).coerceIn(0f, 1f)
                    val rawX = p.minX + nx * (p.maxX - p.minX)
                    val rawY = p.minY + ny * (p.maxY - p.minY)
                    val snappedX = if (p.stepX > 0f && p.stepX.isFinite()) {
                        (p.minX + ((rawX - p.minX) / p.stepX).roundToInt() * p.stepX).coerceIn(p.minX, p.maxX)
                    } else rawX
                    val snappedY = if (p.stepY > 0f && p.stepY.isFinite()) {
                        (p.minY + ((rawY - p.minY) / p.stepY).roundToInt() * p.stepY).coerceIn(p.minY, p.maxY)
                    } else rawY
                    if (snappedX == lastEmittedX && snappedY == lastEmittedY) return
                    lastEmittedX = snappedX
                    lastEmittedY = snappedY
                    latestOnValueChange(snappedX, snappedY)
                }

                awaitEachGesture {
                    lastEmittedX = Float.NaN
                    lastEmittedY = Float.NaN
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isDragging = true
                    try {
                        emitForPosition(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (change.position != change.previousPosition) {
                                change.consume()
                                emitForPosition(change.position)
                            }
                        }
                    } finally {
                        isDragging = false
                    }
                    latestOnCommit()
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotColor = colors.outlineVariant.copy(alpha = 0.5f)
            val centerColor = colors.outlineVariant.copy(alpha = 0.55f)
            val accentColor = colors.primary
            val surfaceColor = colors.surface

            val dotRadius = 1.25.dp.toPx()
            for (ix in 1..5) {
                for (iy in 1..5) {
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(size.width * (ix / 6f), size.height * (iy / 6f))
                    )
                }
            }

            val centerStroke = 0.75.dp.toPx()
            drawLine(
                color = centerColor,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = centerStroke
            )
            drawLine(
                color = centerColor,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = centerStroke
            )

            val cx = (normX * size.width).coerceIn(0f, size.width)
            val cy = (normY * size.height).coerceIn(0f, size.height)

            if (defaultNormX != null && defaultNormY != null &&
                (kotlin.math.abs(defaultNormX - normX) > 0.01f || kotlin.math.abs(defaultNormY - normY) > 0.01f)
            ) {
                drawCircle(
                    color = colors.outline.copy(alpha = 0.7f),
                    radius = 4.5.dp.toPx(),
                    center = Offset(defaultNormX * size.width, defaultNormY * size.height),
                    style = Stroke(width = 1.25.dp.toPx())
                )
            }

            val crosshairAlpha = if (isDragging) 0.55f else 0.3f
            drawLine(
                color = accentColor.copy(alpha = crosshairAlpha),
                start = Offset(cx, 0f),
                end = Offset(cx, size.height),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = accentColor.copy(alpha = crosshairAlpha),
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = 1.dp.toPx()
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.16f),
                radius = haloRadius.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = accentColor,
                radius = 7.5.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = surfaceColor,
                radius = 7.5.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        Text(
            text = "${formatNumberValue(parameter.minX, parameter.stepX)}, ${formatNumberValue(parameter.minY, parameter.stepY)}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Text(
            text = "${formatNumberValue(parameter.maxX, parameter.stepX)}, ${formatNumberValue(parameter.maxY, parameter.stepY)}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkParameterBottomSheet(
    visible: Boolean,
    declarations: List<WorkParameter>,
    parameterValues: Map<String, String>,
    isLandscape: Boolean,
    landscapeEditorOnLeft: Boolean,
    editorWidthFraction: Float,
    showStatusBar: Boolean,
    colors: ColorScheme,
    onChange: (WorkParameter, String) -> Unit,
    onResetParameter: (WorkParameter) -> Unit,
    onResetAll: () -> Unit,
    onApplyDefaults: (() -> Unit)? = null,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    windowSetup: @Composable () -> Unit = {},
    onAddParameter: (() -> Unit)? = null,
    onInsertSample: (() -> Unit)? = null,
    onJumpToParameter: ((String) -> Unit)? = null,
    onCompareHold: ((Boolean) -> Unit)? = null,
    initialDefaults: Map<String, String>? = null,
    presets: List<WorkParameterPreset> = emptyList(),
    onSavePreset: ((String, Map<String, String>, String?) -> Boolean)? = null,
    onRenamePreset: ((String, String) -> Boolean)? = null,
    onDeletePreset: ((String) -> Boolean)? = null,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = isLandscape)
    var sawPartiallyExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(sheetState.currentValue, isLandscape, onToggleFullscreen) {
        if (!isLandscape && onToggleFullscreen != null) {
            if (sheetState.currentValue == SheetValue.PartiallyExpanded) {
                sawPartiallyExpanded = true
            } else if (sawPartiallyExpanded && sheetState.currentValue == SheetValue.Expanded) {
                sawPartiallyExpanded = false
                onToggleFullscreen()
            }
        }
    }

    val panelContent = @Composable {
        WorkParameterPanel(
            parameters = declarations,
            values = parameterValues,
            onChange = onChange,
            onCommit = onCommit,
            text = { s -> textTranslator(s, emptyArray()) },
            onResetParameter = onResetParameter,
            onResetAll = onResetAll,
            onApplyDefaults = onApplyDefaults,
            onDismiss = onDismiss,
            onAddParameter = onAddParameter,
            onInsertSample = onInsertSample,
            onJumpToParameter = onJumpToParameter,
            onCompareHold = onCompareHold,
            initialDefaults = initialDefaults,
            presets = presets,
            onSavePreset = onSavePreset,
            onRenamePreset = onRenamePreset,
            onDeletePreset = onDeletePreset,
            isFullscreen = isFullscreen,
            onToggleFullscreen = onToggleFullscreen
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = !isLandscape,
        shape = RoundedCornerShape(topStart = UNIT_CORNER_RADIUS, topEnd = UNIT_CORNER_RADIUS),
        containerColor = if (isLandscape) Color.Transparent else colors.surface,
        scrimColor = if (isLandscape) MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.scrim.copy(alpha = 0.14f),
        dragHandle = if (isLandscape) null else {
            {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(width = 32.dp, height = 3.dp)
                            .clip(CircleShape)
                            .background(colors.outlineVariant)
                    )
                }
            }
        }
    ) {
        windowSetup()
        if (isLandscape) {
            Box(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                contentAlignment = if (landscapeEditorOnLeft) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(editorWidthFraction)
                        .widthIn(min = 360.dp, max = 560.dp)
                        .fillMaxHeight(),
                    shape = if (landscapeEditorOnLeft)
                        RoundedCornerShape(topEnd = UNIT_CORNER_RADIUS, bottomEnd = UNIT_CORNER_RADIUS)
                    else
                        RoundedCornerShape(topStart = UNIT_CORNER_RADIUS, bottomStart = UNIT_CORNER_RADIUS),
                    color = colors.surface,
                    border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
                    tonalElevation = 0.dp
                ) {
                    panelContent()
                }
            }
        } else {
            panelContent()
        }
    }
}

@Composable
internal fun InlineWorkParameterPanel(
    parameters: List<WorkParameter>,
    values: Map<String, String>,
    onChange: (WorkParameter, String) -> Unit,
    onCommit: () -> Unit,
    text: (String) -> String,
    onResetParameter: ((WorkParameter) -> Unit)? = null,
    onResetAll: (() -> Unit)? = null,
    onApplyDefaults: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onAddParameter: (() -> Unit)? = null,
    onInsertSample: (() -> Unit)? = null,
    onJumpToParameter: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onCompareHold: ((Boolean) -> Unit)? = null,
    initialDefaults: Map<String, String>? = null,
    presets: List<WorkParameterPreset> = emptyList(),
    onSavePreset: ((String, Map<String, String>, String?) -> Boolean)? = null,
    onRenamePreset: ((String, String) -> Boolean)? = null,
    onDeletePreset: ((String) -> Boolean)? = null,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .fillMaxSize()
            .border(
                width = 1.dp,
                color = colors.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        WorkParameterPanel(
            parameters = parameters,
            values = values,
            onChange = onChange,
            onCommit = onCommit,
            text = text,
            onResetParameter = onResetParameter,
            onResetAll = onResetAll,
            onApplyDefaults = onApplyDefaults,
            onDismiss = onDismiss,
            onAddParameter = onAddParameter,
            onInsertSample = onInsertSample,
            onJumpToParameter = onJumpToParameter,
            topPadding = 16.dp,
            onCompareHold = onCompareHold,
            initialDefaults = initialDefaults,
            presets = presets,
            onSavePreset = onSavePreset,
            onRenamePreset = onRenamePreset,
            onDeletePreset = onDeletePreset,
            isFullscreen = isFullscreen,
            onToggleFullscreen = onToggleFullscreen
        )
    }
}

