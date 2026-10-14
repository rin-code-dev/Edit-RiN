package com.hikariatelier.app

import org.json.JSONObject

internal sealed class WorkParameter(open val name: String, open val label: String, open val defaultValue: String) {
    data class Number(override val name: String, override val label: String, override val defaultValue: String,
                      val min: Float, val max: Float, val step: Float) : WorkParameter(name, label, defaultValue)
    data class Color(override val name: String, override val label: String, override val defaultValue: String) :
        WorkParameter(name, label, defaultValue)
    data class Boolean(override val name: String, override val label: String, override val defaultValue: String) :
        WorkParameter(name, label, defaultValue)
    data class Choice(override val name: String, override val label: String, override val defaultValue: String,
                      val options: List<String>) : WorkParameter(name, label, defaultValue)
}

private val parameterLine = Regex("^\\s*//\\s*@rin\\s+(number|color|boolean|choice)\\s+([A-Za-z_$][\\w$]*)\\s+\"([^\"]{1,40})\"\\s+(.+?)\\s*$")
private val hexColor = Regex("#[0-9a-fA-F]{6}")
private val choiceToken = Regex("^[A-Za-z0-9_.-]{1,32}$")

/** Parameter declarations are comments, so older app versions can still run the sketch. */
internal fun workParameters(sources: Map<String, String>): List<WorkParameter> = buildList {
    val names = mutableSetOf<String>()
    for ((file, source) in sources) if (isJavaScriptProjectFile(file)) for (line in source.lineSequence()) {
        if (size >= 16) return@buildList
        val match = parameterLine.matchEntire(line) ?: continue
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
                    else WorkParameter.Number(name, label, initial.toString(), min, max, step)
                }
            }
            "color" -> parts.singleOrNull()?.takeIf { hexColor.matches(it) }?.let {
                WorkParameter.Color(name, label, it.uppercase())
            }
            "boolean" -> parts.singleOrNull()?.lowercase()?.takeIf { it == "true" || it == "false" }?.let {
                WorkParameter.Boolean(name, label, it)
            }
            "choice" -> {
                if (parts.size < 2) null else {
                    val defaultVal = parts[0]
                    val rawOptions = if (parts.drop(1).contains(defaultVal)) parts.drop(1) else parts
                    val options = rawOptions.distinct()
                    if (options.size < 2 || defaultVal !in options || options.any { !choiceToken.matches(it) }) null
                    else WorkParameter.Choice(name, label, defaultVal, options)
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
 * Rewrites the default value of each `// @rin` declaration in [source] to the current value in [values].
 * Returns the new text and the names whose declaration changed, or null when nothing differs.
 */
internal fun applyParameterDefaults(source: String, values: Map<String, String>): Pair<String, Set<String>>? {
    val changed = mutableSetOf<String>()
    val lines = source.split("\n").map { line ->
        val match = parameterLine.matchEntire(line.trimEnd('\r')) ?: return@map line
        val kind = match.groupValues[1]
        val name = match.groupValues[2]
        val saved = values[name] ?: return@map line
        val parameter = workParameters(mapOf("sketch.js" to line)).singleOrNull() ?: return@map line
        val current = parameterValue(parameter, saved)
        val range = match.groups[4]?.range ?: return@map line
        val parts = match.groupValues[4].trim().split(Regex("\\s+")).toMutableList()
        val replaced = when (parameter) {
            is WorkParameter.Number -> {
                val next = formatNumberValue(current.toFloat(), parameter.step)
                if (kotlin.math.abs(next.toFloat() - parameter.defaultValue.toFloat()) <= 0.0001f) return@map line
                parts[2] = next; true
            }
            is WorkParameter.Color -> {
                if (current.equals(parameter.defaultValue, true)) return@map line
                parts[0] = current.uppercase(); true
            }
            is WorkParameter.Boolean -> {
                if (current.equals(parameter.defaultValue, true)) return@map line
                parts[0] = current.lowercase(); true
            }
            is WorkParameter.Choice -> {
                if (current == parameter.defaultValue) return@map line
                parts.clear()
                parts.add(current)
                parts.addAll(parameter.options)
                true
            }
        }
        if (!replaced || kind.isEmpty()) return@map line
        changed += name
        line.substring(0, range.first) + parts.joinToString(" ") + (if (line.endsWith("\r")) "\r" else "")
    }
    return if (changed.isEmpty()) null else lines.joinToString("\n") to changed
}

private val RIN_DECLARATION_LINE_REGEX = Regex("""^\s*//\s*@rin\s+(?:number|color|boolean|choice)\s+([A-Za-z][A-Za-z0-9_]*)\b""")

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
            val match = RIN_DECLARATION_LINE_REGEX.find(line)
            if (match != null && match.groupValues[1] == parameterName) {
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
