package com.hikariatelier.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val MAX_PARAMETER_PRESETS = 12
internal const val MAX_PARAMETER_PRESET_NAME = 40

data class WorkParameterPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val values: Map<String, String>
)

internal fun normalizeParameterPresets(presets: List<WorkParameterPreset>): List<WorkParameterPreset> {
    val seenIds = mutableSetOf<String>()
    return buildList {
        for (preset in presets) {
            if (size >= MAX_PARAMETER_PRESETS) break
            val id = preset.id.trim().ifEmpty { UUID.randomUUID().toString() }
            if (!seenIds.add(id)) continue
            val name = preset.name.trim().take(MAX_PARAMETER_PRESET_NAME)
            if (name.isEmpty()) continue
            val values = linkedMapOf<String, String>()
            preset.values.entries.take(16).forEach { (k, v) ->
                if (k.isNotBlank()) values[k] = v
            }
            add(WorkParameterPreset(id = id, name = name, values = values))
        }
    }
}

internal sealed class WorkParameter(
    open val name: String,
    open val label: String,
    open val defaultValue: String,
    open val sourceFile: String = "sketch.js"
) {
    data class Number(override val name: String, override val label: String, override val defaultValue: String,
                      val min: Float, val max: Float, val step: Float,
                      override val sourceFile: String = "sketch.js") : WorkParameter(name, label, defaultValue, sourceFile)
    data class Color(override val name: String, override val label: String, override val defaultValue: String,
                     override val sourceFile: String = "sketch.js") : WorkParameter(name, label, defaultValue, sourceFile)
    data class Boolean(override val name: String, override val label: String, override val defaultValue: String,
                       override val sourceFile: String = "sketch.js") : WorkParameter(name, label, defaultValue, sourceFile)
    data class Choice(override val name: String, override val label: String, override val defaultValue: String,
                      val options: List<String>,
                      override val sourceFile: String = "sketch.js") : WorkParameter(name, label, defaultValue, sourceFile)
    data class Point2D(
        override val name: String,
        override val label: String,
        override val defaultValue: String,
        val minX: Float,
        val maxX: Float,
        val stepX: Float,
        val minY: Float,
        val maxY: Float,
        val stepY: Float,
        val arrayStyle: kotlin.Boolean = false,
        override val sourceFile: String = "sketch.js"
    ) : WorkParameter(name, label, defaultValue, sourceFile)
}

private val parameterLine = Regex("^\\s*//\\s*@rin\\s+(number|color|boolean|choice|xy)\\s+([A-Za-z_$][\\w$]*)\\s+\"([^\"]{1,40})\"\\s+(.+?)\\s*$")
private val hexColor = Regex("#[0-9a-fA-F]{6}")
private val choiceToken = Regex("^[A-Za-z0-9_.-]{1,32}$")
private val inlineParameterRegex = Regex(
    """^\s*(?:(?:let|const|var)\s+)?([A-Za-z_$][\w$]*)\s*=\s*(.+?)\s*;?\s*//\s*@(slider|int|color|toggle|choice|xy)(?:\(([^)]*)\))?(?:\s+(?:\"([^\"]+)\"|([^\n\r]+)))?\s*$"""
)
private val point2DArrayLiteralRegex = Regex("""^\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\]$""")
private val point2DObjectXYLiteralRegex = Regex("""^\{\s*x\s*:\s*(-?\d+(?:\.\d+)?)\s*,\s*y\s*:\s*(-?\d+(?:\.\d+)?)\s*\}$""")
private val point2DObjectYXLiteralRegex = Regex("""^\{\s*y\s*:\s*(-?\d+(?:\.\d+)?)\s*,\s*x\s*:\s*(-?\d+(?:\.\d+)?)\s*\}$""")

internal fun parsePoint2DValue(raw: String?): Pair<Float, Float>? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.split(',')
    if (parts.size != 2) return null
    val x = parts[0].trim().toFloatOrNull() ?: return null
    val y = parts[1].trim().toFloatOrNull() ?: return null
    if (!x.isFinite() || !y.isFinite()) return null
    return x to y
}

internal fun formatPoint2DValue(x: Float, y: Float, stepX: Float, stepY: Float): String =
    "${formatNumberValue(x, stepX)},${formatNumberValue(y, stepY)}"

private fun parsePoint2DLiteral(literal: String): Triple<Float, Float, kotlin.Boolean>? {
    val trimmed = literal.trim()
    point2DArrayLiteralRegex.matchEntire(trimmed)?.let { m ->
        val x = m.groupValues[1].toFloatOrNull() ?: return null
        val y = m.groupValues[2].toFloatOrNull() ?: return null
        if (!x.isFinite() || !y.isFinite()) return null
        return Triple(x, y, true)
    }
    point2DObjectXYLiteralRegex.matchEntire(trimmed)?.let { m ->
        val x = m.groupValues[1].toFloatOrNull() ?: return null
        val y = m.groupValues[2].toFloatOrNull() ?: return null
        if (!x.isFinite() || !y.isFinite()) return null
        return Triple(x, y, false)
    }
    point2DObjectYXLiteralRegex.matchEntire(trimmed)?.let { m ->
        val y = m.groupValues[1].toFloatOrNull() ?: return null
        val x = m.groupValues[2].toFloatOrNull() ?: return null
        if (!x.isFinite() || !y.isFinite()) return null
        return Triple(x, y, false)
    }
    return null
}

/** Parameter declarations are comments, so older app versions can still run the sketch. */
internal fun workParameters(sources: Map<String, String>): List<WorkParameter> = buildList {
    val names = mutableSetOf<String>()
    for ((file, source) in sources) if (isJavaScriptProjectFile(file)) for (line in source.lineSequence()) {
        if (size >= 16) return@buildList
        val trimmed = line.trimEnd('\r')

        // 1. Try legacy // @rin syntax
        val match = parameterLine.matchEntire(trimmed)
        if (match != null) {
            val kind = match.groupValues[1]
            val name = match.groupValues[2]
            if (name in names) continue
            val label = match.groupValues[3]
            val parts = match.groupValues[4].trim().split(Regex("\\s+"))
            val parameter = when (kind) {
                "number" -> {
                    if (parts.size != 4) null else {
                        val values = parts.map { it.toFloatOrNull() }
                        val min = values[0]; val max = values[1]; val initial = values[2]; val step = values[3]
                        if (min == null || max == null || initial == null || step == null ||
                            !min.isFinite() || !max.isFinite() || !initial.isFinite() || !step.isFinite() ||
                            min >= max || initial !in min..max || step <= 0f) null
                        else WorkParameter.Number(name, label, initial.toString(), min, max, step, file)
                    }
                }
                "color" -> parts.singleOrNull()?.takeIf { hexColor.matches(it) }?.let {
                    WorkParameter.Color(name, label, it.uppercase(), file)
                }
                "boolean" -> parts.singleOrNull()?.lowercase()?.takeIf { it == "true" || it == "false" }?.let {
                    WorkParameter.Boolean(name, label, it, file)
                }
                "choice" -> {
                    if (parts.size < 2) null else {
                        val defaultVal = parts[0]
                        val rawOptions = if (parts.drop(1).contains(defaultVal)) parts.drop(1) else parts
                        val options = rawOptions.distinct()
                        if (options.size < 2 || defaultVal !in options || options.any { !choiceToken.matches(it) }) null
                        else WorkParameter.Choice(name, label, defaultVal, options, file)
                    }
                }
                "xy" -> {
                    if (parts.size !in 6..8) null else {
                        val values = parts.map { it.toFloatOrNull() }
                        val minX = values[0]; val maxX = values[1]; val initX = values[2]
                        val minY = values[3]; val maxY = values[4]; val initY = values[5]
                        val stepX = if (parts.size >= 7) values[6] else if (minX != null && maxX != null) (maxX - minX) / 100f else null
                        val stepY = if (parts.size == 8) values[7] else if (parts.size == 7) values[6] else if (minY != null && maxY != null) (maxY - minY) / 100f else null
                        if (minX == null || maxX == null || initX == null ||
                            minY == null || maxY == null || initY == null ||
                            stepX == null || stepY == null ||
                            !minX.isFinite() || !maxX.isFinite() || !initX.isFinite() ||
                            !minY.isFinite() || !maxY.isFinite() || !initY.isFinite() ||
                            !stepX.isFinite() || !stepY.isFinite() ||
                            minX >= maxX || minY >= maxY ||
                            initX !in minX..maxX || initY !in minY..maxY ||
                            stepX <= 0f || stepY <= 0f) null
                        else WorkParameter.Point2D(
                            name = name,
                            label = label,
                            defaultValue = formatPoint2DValue(initX, initY, stepX, stepY),
                            minX = minX,
                            maxX = maxX,
                            stepX = stepX,
                            minY = minY,
                            maxY = maxY,
                            stepY = stepY,
                            arrayStyle = false,
                            sourceFile = file
                        )
                    }
                }
                else -> null
            }
            if (parameter != null) { add(parameter); names += name }
            continue
        }

        // 2. Try inline comment syntax: let x = ...; // @slider(...)
        val inlineMatch = inlineParameterRegex.matchEntire(trimmed) ?: continue
        val name = inlineMatch.groupValues[1]
        if (name in names) continue
        val valueLiteral = inlineMatch.groupValues[2].trim()
        val kind = inlineMatch.groupValues[3]
        val argsStr = inlineMatch.groupValues[4]
        val label = inlineMatch.groups[5]?.value?.trim()
            ?: inlineMatch.groups[6]?.value?.trim()?.takeIf { it.isNotEmpty() }
            ?: name

        val parameter = when (kind) {
            "slider" -> {
                val parts = argsStr.split(Regex("""[\s,]+""")).filter { it.isNotEmpty() }.mapNotNull { it.toFloatOrNull() }
                if (parts.size < 2) null else {
                    val min = parts[0]
                    val max = parts[1]
                    if (!min.isFinite() || !max.isFinite() || min >= max) null else {
                        val step = if (parts.size >= 3) parts[2] else ((max - min) / 100f)
                        if (!step.isFinite() || step <= 0f) null else {
                            val initial = (valueLiteral.toFloatOrNull() ?: min).coerceIn(min, max)
                            WorkParameter.Number(name, label, formatNumberValue(initial, step), min, max, step, file)
                        }
                    }
                }
            }
            "int" -> {
                val parts = argsStr.split(Regex("""[\s,]+""")).filter { it.isNotEmpty() }.mapNotNull { it.toFloatOrNull() }
                if (parts.size < 2) null else {
                    val min = parts[0]
                    val max = parts[1]
                    if (!min.isFinite() || !max.isFinite() || min >= max) null else {
                        val step = 1f
                        val initial = (valueLiteral.toIntOrNull()?.toFloat() ?: min).coerceIn(min, max)
                        WorkParameter.Number(name, label, initial.toInt().toString(), min, max, step, file)
                    }
                }
            }
            "color" -> {
                val clean = valueLiteral.removeSurrounding("\"").removeSurrounding("'")
                if (hexColor.matches(clean)) {
                    WorkParameter.Color(name, label, clean.uppercase(), file)
                } else null
            }
            "toggle" -> {
                val clean = valueLiteral.lowercase()
                if (clean == "true" || clean == "false") {
                    WorkParameter.Boolean(name, label, clean, file)
                } else null
            }
            "choice" -> {
                val options = argsStr.split(",")
                    .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                    .filter { choiceToken.matches(it) }
                    .distinct()
                if (options.size < 2) null else {
                    val cleanVal = valueLiteral.removeSurrounding("\"").removeSurrounding("'")
                    val defaultVal = if (cleanVal in options) cleanVal else options.first()
                    WorkParameter.Choice(name, label, defaultVal, options, file)
                }
            }
            "xy" -> {
                val literal = parsePoint2DLiteral(valueLiteral)
                val parts = argsStr.split(Regex("""[\s,]+""")).filter { it.isNotEmpty() }.mapNotNull { it.toFloatOrNull() }
                if (literal == null || parts.size !in 2..6) null else {
                    val (rawX, rawY, isArray) = literal
                    val bounds = when (parts.size) {
                        2 -> floatArrayOf(parts[0], parts[1], parts[0], parts[1], (parts[1] - parts[0]) / 100f, (parts[1] - parts[0]) / 100f)
                        3 -> floatArrayOf(parts[0], parts[1], parts[0], parts[1], parts[2], parts[2])
                        4 -> floatArrayOf(parts[0], parts[1], parts[2], parts[3], (parts[1] - parts[0]) / 100f, (parts[3] - parts[2]) / 100f)
                        5 -> floatArrayOf(parts[0], parts[1], parts[2], parts[3], parts[4], parts[4])
                        else -> floatArrayOf(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
                    }
                    val minX = bounds[0]; val maxX = bounds[1]
                    val minY = bounds[2]; val maxY = bounds[3]
                    val stepX = bounds[4]; val stepY = bounds[5]
                    if (!minX.isFinite() || !maxX.isFinite() || !minY.isFinite() || !maxY.isFinite() ||
                        !stepX.isFinite() || !stepY.isFinite() ||
                        minX >= maxX || minY >= maxY || stepX <= 0f || stepY <= 0f) null
                    else {
                        val initX = rawX.coerceIn(minX, maxX)
                        val initY = rawY.coerceIn(minY, maxY)
                        WorkParameter.Point2D(
                            name = name,
                            label = label,
                            defaultValue = formatPoint2DValue(initX, initY, stepX, stepY),
                            minX = minX,
                            maxX = maxX,
                            stepX = stepX,
                            minY = minY,
                            maxY = maxY,
                            stepY = stepY,
                            arrayStyle = isArray,
                            sourceFile = file
                        )
                    }
                }
            }
            else -> null
        }
        if (parameter != null) { add(parameter); names += name }
    }
}

/** Annotated constants are mutable only in the runtime copy; authored declarations stay intact. */
internal fun prepareLiveParameterSource(source: String): String = source.split("\n").joinToString("\n") { line ->
    val match = inlineParameterRegex.matchEntire(line.trimEnd('\r'))
    if (match != null && workParameters(mapOf("sketch.js" to line)).isNotEmpty()) {
        val runtimeLine = line.replaceFirst(Regex("""^(\s*)const\b"""), "$1let")
        val comment = runtimeLine.lastIndexOf("//")
        val name = match.groupValues[1]
        val code = runtimeLine.substring(0, comment).trimEnd().removeSuffix(";")
        // A closure updates the actual binding even in modules or nested scopes. Keep line numbers.
        code + ";(window.__editRinParameterBindings ||= Object.create(null))[" +
            JSONObject.quote(name) + "] = v => { $name = v }; " + runtimeLine.substring(comment)
    } else line
}

internal fun parameterValue(parameter: WorkParameter, saved: String?): String = when (parameter) {
    is WorkParameter.Number -> saved?.toFloatOrNull()?.takeIf { it.isFinite() && it in parameter.min..parameter.max }
        ?.toString() ?: parameter.defaultValue
    is WorkParameter.Color -> saved?.takeIf { hexColor.matches(it) }?.uppercase() ?: parameter.defaultValue
    is WorkParameter.Boolean -> when (saved?.lowercase()) {
        "true" -> "true"
        "false" -> "false"
        else -> parameter.defaultValue
    }
    is WorkParameter.Choice -> saved?.takeIf { it in parameter.options } ?: parameter.defaultValue
    is WorkParameter.Point2D -> parsePoint2DValue(saved)
        ?.takeIf { (x, y) -> x in parameter.minX..parameter.maxX && y in parameter.minY..parameter.maxY }
        ?.let { (x, y) -> formatPoint2DValue(x, y, parameter.stepX, parameter.stepY) }
        ?: parameter.defaultValue
}

internal fun parameterToJsonValue(parameter: WorkParameter, rawValue: String?): String {
    val value = parameterValue(parameter, rawValue)
    return when (parameter) {
        is WorkParameter.Number, is WorkParameter.Boolean -> value
        is WorkParameter.Color, is WorkParameter.Choice -> JSONObject.quote(value)
        is WorkParameter.Point2D -> {
            val (x, y) = parsePoint2DValue(value) ?: parsePoint2DValue(parameter.defaultValue) ?: (parameter.minX to parameter.minY)
            if (parameter.arrayStyle) {
                JSONArray().put(x.toDouble()).put(y.toDouble()).toString()
            } else {
                JSONObject().put("x", x.toDouble()).put("y", y.toDouble()).toString()
            }
        }
    }
}

internal fun parameterValuesJson(parameters: List<WorkParameter>, values: Map<String, String>): String =
    JSONObject().apply {
        parameters.forEach { parameter ->
            val value = parameterValue(parameter, values[parameter.name])
            when (parameter) {
                is WorkParameter.Number -> put(parameter.name, value.toDouble())
                is WorkParameter.Boolean -> put(parameter.name, value.toBoolean())
                is WorkParameter.Color -> put(parameter.name, value)
                is WorkParameter.Choice -> put(parameter.name, value)
                is WorkParameter.Point2D -> {
                    val (x, y) = parsePoint2DValue(value) ?: parsePoint2DValue(parameter.defaultValue) ?: (parameter.minX to parameter.minY)
                    if (parameter.arrayStyle) {
                        put(parameter.name, JSONArray().put(x.toDouble()).put(y.toDouble()))
                    } else {
                        put(parameter.name, JSONObject().put("x", x.toDouble()).put("y", y.toDouble()))
                    }
                }
            }
        }
    }.toString()

/**
 * Replaces the literal value of [parameter] in [source] with [newValue].
 * Returns the modified source or null if nothing changed or the declaration was not found.
 */
internal fun updateParameterInSource(source: String, parameter: WorkParameter, newValue: String): String? {
    var changed = false
    val lines = source.split("\n").map { line ->
        val trimmed = line.trimEnd('\r')

        // 1. Check inline parameter syntax
        val inlineMatch = inlineParameterRegex.matchEntire(trimmed)
        if (inlineMatch != null && inlineMatch.groupValues[1] == parameter.name) {
            val range = inlineMatch.groups[2]?.range ?: return@map line
            val oldValText = inlineMatch.groupValues[2]
            val formattedNewVal = when (parameter) {
                is WorkParameter.Number -> formatNumberValue(newValue.toFloatOrNull() ?: parameter.defaultValue.toFloat(), parameter.step)
                is WorkParameter.Color -> {
                    val quote = if (oldValText.trim().startsWith("'")) "'" else "\""
                    "$quote${newValue.uppercase()}$quote"
                }
                is WorkParameter.Boolean -> newValue.lowercase()
                is WorkParameter.Choice -> {
                    val trimmedVal = oldValText.trim()
                    val quote = if (trimmedVal.startsWith("'")) "'" else if (trimmedVal.startsWith("\"")) "\"" else ""
                    "$quote$newValue$quote"
                }
                is WorkParameter.Point2D -> {
                    val (x, y) = parsePoint2DValue(newValue) ?: parsePoint2DValue(parameter.defaultValue) ?: (parameter.minX to parameter.minY)
                    val fx = formatNumberValue(x.coerceIn(parameter.minX, parameter.maxX), parameter.stepX)
                    val fy = formatNumberValue(y.coerceIn(parameter.minY, parameter.maxY), parameter.stepY)
                    if (parameter.arrayStyle) "[$fx, $fy]" else "{ x: $fx, y: $fy }"
                }
            }
            if (oldValText.trim() != formattedNewVal) {
                changed = true
                val cr = if (line.endsWith("\r")) "\r" else ""
                val updated = trimmed.substring(0, range.first) + formattedNewVal + trimmed.substring(range.last + 1)
                return@map updated + cr
            }
            return@map line
        }

        // 2. Check legacy @rin syntax
        val rinMatch = parameterLine.matchEntire(trimmed)
        if (rinMatch != null && rinMatch.groupValues[2] == parameter.name) {
            val range = rinMatch.groups[4]?.range ?: return@map line
            val parts = rinMatch.groupValues[4].trim().split(Regex("\\s+")).toMutableList()
            var replaced = false
            when (parameter) {
                is WorkParameter.Number -> {
                    val next = formatNumberValue(newValue.toFloatOrNull() ?: parameter.defaultValue.toFloat(), parameter.step)
                    if (parts.size >= 3 && parts[2] != next) {
                        parts[2] = next
                        replaced = true
                    }
                }
                is WorkParameter.Color -> {
                    if (parts.isNotEmpty() && !parts[0].equals(newValue, true)) {
                        parts[0] = newValue.uppercase()
                        replaced = true
                    }
                }
                is WorkParameter.Boolean -> {
                    if (parts.isNotEmpty() && !parts[0].equals(newValue, true)) {
                        parts[0] = newValue.lowercase()
                        replaced = true
                    }
                }
                is WorkParameter.Choice -> {
                    if (newValue != parameter.defaultValue) {
                        parts.clear()
                        parts.add(newValue)
                        parts.addAll(parameter.options)
                        replaced = true
                    }
                }
                is WorkParameter.Point2D -> {
                    val (x, y) = parsePoint2DValue(newValue) ?: parsePoint2DValue(parameter.defaultValue) ?: (parameter.minX to parameter.minY)
                    val fx = formatNumberValue(x.coerceIn(parameter.minX, parameter.maxX), parameter.stepX)
                    val fy = formatNumberValue(y.coerceIn(parameter.minY, parameter.maxY), parameter.stepY)
                    if (parts.size >= 6 && (parts[2] != fx || parts[5] != fy)) {
                        parts[2] = fx
                        parts[5] = fy
                        replaced = true
                    }
                }
            }
            if (replaced) {
                changed = true
                val cr = if (line.endsWith("\r")) "\r" else ""
                return@map trimmed.substring(0, range.first) + parts.joinToString(" ") + cr
            }
        }
        line
    }
    return if (changed) lines.joinToString("\n") else null
}

/**
 * Rewrites the default value of each declaration in [source] to the current value in [values].
 * Returns the new text and the names whose declaration changed, or null when nothing differs.
 */
internal fun applyParameterDefaults(source: String, values: Map<String, String>): Pair<String, Set<String>>? {
    val changed = mutableSetOf<String>()
    var currentSource = source
    val declarations = workParameters(mapOf("sketch.js" to source))
    for (parameter in declarations) {
        val saved = values[parameter.name] ?: continue
        val current = parameterValue(parameter, saved)
        val updated = updateParameterInSource(currentSource, parameter, current)
        if (updated != null && updated != currentSource) {
            currentSource = updated
            changed += parameter.name
        }
    }
    return if (changed.isEmpty()) null else currentSource to changed
}

private val PARAM_DECLARATION_LINE_REGEX = Regex(
    """^\s*//\s*@rin\s+(?:number|color|boolean|choice|xy)\s+([A-Za-z][A-Za-z0-9_]*)\b|^\s*(?:(?:let|const|var)\s+)?([A-Za-z_$][\w$]*)\s*=\s*.+?//\s*@(slider|int|color|toggle|choice|xy)\b"""
)

internal fun findParameterDeclarationLine(
    sources: Map<String, String>,
    parameterName: String
): Pair<String, Int>? {
    val orderedFiles = buildList {
        if ("sketch.js" in sources) add("sketch.js")
        addAll(sources.keys.filter { it != "sketch.js" }.sorted())
    }
    for (file in orderedFiles) {
        val code = sources[file] ?: continue
        code.lineSequence().forEachIndexed { index, line ->
            val match = PARAM_DECLARATION_LINE_REGEX.find(line)
            if (match != null && (match.groupValues[1] == parameterName || match.groupValues[2] == parameterName)) {
                return file to (index + 1)
            }
        }
    }
    return null
}

private fun randomSteppedFloat(min: Float, max: Float, step: Float, random: kotlin.random.Random): Float {
    val minD = min.toDouble()
    val maxD = max.toDouble()
    val range = (maxD - minD).coerceAtLeast(0.0)
    val stepD = step.toDouble().takeIf { it > 0.0 && it.isFinite() } ?: 1.0
    val stepsCount = if (range > 0.0) (range / stepD).toInt() else 0
    val rawValue = if (stepsCount > 0) {
        (minD + random.nextInt(0, stepsCount + 1) * stepD).coerceIn(minD, maxD)
    } else {
        minD + random.nextDouble() * range
    }
    return rawValue.toFloat()
}

internal fun randomizeWorkParameters(
    parameters: List<WorkParameter>,
    currentValues: Map<String, String>,
    lockedNames: Set<String>,
    random: kotlin.random.Random = kotlin.random.Random.Default
): Map<String, String> {
    val updated = currentValues.toMutableMap()
    for (param in parameters) {
        if (param.name in lockedNames) continue
        when (param) {
            is WorkParameter.Color -> {
                val r = random.nextInt(32, 240)
                val g = random.nextInt(32, 240)
                val b = random.nextInt(32, 240)
                updated[param.name] = String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b)
            }
            is WorkParameter.Boolean -> {
                updated[param.name] = random.nextBoolean().toString()
            }
            is WorkParameter.Choice -> {
                if (param.options.isNotEmpty()) {
                    updated[param.name] = param.options[random.nextInt(param.options.size)]
                }
            }
            is WorkParameter.Number -> {
                val rawValue = randomSteppedFloat(param.min, param.max, param.step, random).toDouble()
                val stepText = param.step.toString()
                val decimals = if ('.' in stepText) stepText.substringAfter('.').trimEnd('0').length.coerceIn(0, 4) else 0
                updated[param.name] = formatScrubbedNumber(rawValue, decimals)
            }
            is WorkParameter.Point2D -> {
                val rx = randomSteppedFloat(param.minX, param.maxX, param.stepX, random)
                val ry = randomSteppedFloat(param.minY, param.maxY, param.stepY, random)
                updated[param.name] = formatPoint2DValue(rx, ry, param.stepX, param.stepY)
            }
        }
    }
    return updated
}

internal fun isParameterModified(parameter: WorkParameter, currentRaw: String?, defaultRaw: String?): Boolean {
    val cur = parameterValue(parameter, currentRaw)
    val def = parameterValue(parameter, defaultRaw)
    return when (parameter) {
        is WorkParameter.Color -> !cur.equals(def, ignoreCase = true)
        is WorkParameter.Boolean -> !cur.equals(def, ignoreCase = true)
        is WorkParameter.Choice -> cur != def
        is WorkParameter.Number -> {
            val curF = cur.toFloatOrNull() ?: 0f
            val defF = def.toFloatOrNull() ?: 0f
            abs(curF - defF) > 0.0001f
        }
        is WorkParameter.Point2D -> {
            val (cx, cy) = parsePoint2DValue(cur) ?: return false
            val (dx, dy) = parsePoint2DValue(def) ?: return false
            abs(cx - dx) > 0.0001f || abs(cy - dy) > 0.0001f
        }
    }
}

internal fun parameterValuesMatch(
    parameters: List<WorkParameter>,
    a: Map<String, String>,
    b: Map<String, String>
): Boolean = parameters.isNotEmpty() && parameters.all { p ->
    !isParameterModified(p, a[p.name], b[p.name])
}

internal fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun snapInterpolatedFloat(from: Float, to: Float, fraction: Float, min: Float, max: Float, step: Float): Float {
    val raw = (from + (to - from) * fraction).coerceIn(min, max)
    if (!step.isFinite() || step <= 0f) return raw
    val steps = ((raw - min) / step).roundToInt()
    return (min + steps * step).coerceIn(min, max)
}

internal fun interpolateWorkParameters(
    parameters: List<WorkParameter>,
    fromValues: Map<String, String>,
    toValues: Map<String, String>,
    fraction: Float,
    lockedNames: Set<String> = emptySet()
): Map<String, String> {
    val t = fraction.coerceIn(0f, 1f)
    val result = linkedMapOf<String, String>()
    for (param in parameters) {
        val fromStr = parameterValue(param, fromValues[param.name])
        if (param.name in lockedNames || t <= 0f) {
            result[param.name] = fromStr
            continue
        }
        val toStr = parameterValue(param, toValues[param.name])
        if (t >= 1f) {
            result[param.name] = toStr
            continue
        }
        result[param.name] = when (param) {
            is WorkParameter.Number -> {
                val a = fromStr.toFloatOrNull() ?: param.min
                val b = toStr.toFloatOrNull() ?: a
                val v = snapInterpolatedFloat(a, b, t, param.min, param.max, param.step)
                formatNumberValue(v, param.step)
            }
            is WorkParameter.Point2D -> {
                val (ax, ay) = parsePoint2DValue(fromStr) ?: (param.minX to param.minY)
                val (bx, by) = parsePoint2DValue(toStr) ?: (ax to ay)
                val x = snapInterpolatedFloat(ax, bx, t, param.minX, param.maxX, param.stepX)
                val y = snapInterpolatedFloat(ay, by, t, param.minY, param.maxY, param.stepY)
                formatPoint2DValue(x, y, param.stepX, param.stepY)
            }
            is WorkParameter.Color -> {
                val aHex = fromStr.removePrefix("#").toIntOrNull(16) ?: 0xFFFFFF
                val bHex = toStr.removePrefix("#").toIntOrNull(16) ?: aHex
                val ar = (aHex shr 16) and 0xFF
                val ag = (aHex shr 8) and 0xFF
                val ab = aHex and 0xFF
                val br = (bHex shr 16) and 0xFF
                val bg = (bHex shr 8) and 0xFF
                val bb = bHex and 0xFF
                val r = (ar + (br - ar) * t).roundToInt().coerceIn(0, 255)
                val g = (ag + (bg - ag) * t).roundToInt().coerceIn(0, 255)
                val b = (ab + (bb - ab) * t).roundToInt().coerceIn(0, 255)
                String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b)
            }
            is WorkParameter.Boolean -> if (t < 0.5f) fromStr else toStr
            is WorkParameter.Choice -> if (t < 0.5f) fromStr else toStr
        }
    }
    return result
}
