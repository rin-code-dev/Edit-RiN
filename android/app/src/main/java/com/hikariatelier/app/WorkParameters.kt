package com.hikariatelier.app

import org.json.JSONObject

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
}

private val parameterLine = Regex("^\\s*//\\s*@rin\\s+(number|color|boolean|choice)\\s+([A-Za-z_$][\\w$]*)\\s+\"([^\"]{1,40})\"\\s+(.+?)\\s*$")
private val hexColor = Regex("#[0-9a-fA-F]{6}")
private val choiceToken = Regex("^[A-Za-z0-9_.-]{1,32}$")
private val inlineParameterRegex = Regex(
    """^\s*(?:(?:let|const|var)\s+)?([A-Za-z_$][\w$]*)\s*=\s*(.+?)\s*;?\s*//\s*@(slider|int|color|toggle|choice)(?:\(([^)]*)\))?(?:\s+(?:\"([^\"]+)\"|([^\n\r]+)))?\s*$"""
)

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
            else -> null
        }
        if (parameter != null) { add(parameter); names += name }
    }
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
            }
            if (oldValText.trim() != formattedNewVal) {
                changed = true
                val cr = if (line.endsWith("\r")) "\r" else ""
                var updated = trimmed.substring(0, range.first) + formattedNewVal + trimmed.substring(range.last + 1)
                if (updated.matches(Regex("""^\s*const\s+.*"""))) {
                    updated = updated.replaceFirst(Regex("""\bconst\b"""), "let")
                }
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
    """^\s*//\s*@rin\s+(?:number|color|boolean|choice)\s+([A-Za-z][A-Za-z0-9_]*)\b|^\s*(?:(?:let|const|var)\s+)?([A-Za-z_$][\w$]*)\s*=\s*.+?//\s*@(slider|int|color|toggle|choice)\b"""
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
                updated[param.name] = String.format(java.util.Locale.ROOT, "#%02X%02X%02X", r, g, b)
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
                val min = param.min.toDouble()
                val max = param.max.toDouble()
                val range = (max - min).coerceAtLeast(0.0)
                val step = param.step.toDouble().takeIf { it > 0.0 && it.isFinite() } ?: 1.0
                val stepsCount = if (range > 0.0) (range / step).toInt() else 0
                val rawValue = if (stepsCount > 0) {
                    (min + random.nextInt(0, stepsCount + 1) * step).coerceIn(min, max)
                } else {
                    min + random.nextDouble() * range
                }
                val stepText = param.step.toString()
                val decimals = if ('.' in stepText) stepText.substringAfter('.').trimEnd('0').length.coerceIn(0, 4) else 0
                updated[param.name] = formatScrubbedNumber(rawValue, decimals)
            }
        }
    }
    return updated
}
