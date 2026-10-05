package com.hikariatelier.app

import org.json.JSONObject

internal sealed class WorkParameter(open val name: String, open val label: String, open val defaultValue: String) {
    data class Number(override val name: String, override val label: String, override val defaultValue: String,
                      val min: Float, val max: Float, val step: Float) : WorkParameter(name, label, defaultValue)
    data class Color(override val name: String, override val label: String, override val defaultValue: String) :
        WorkParameter(name, label, defaultValue)
    data class Boolean(override val name: String, override val label: String, override val defaultValue: String) :
        WorkParameter(name, label, defaultValue)
}

private val parameterLine = Regex("^\\s*//\\s*@rin\\s+(number|color|boolean)\\s+([A-Za-z_$][\\w$]*)\\s+\"([^\"]{1,40})\"\\s+(.+?)\\s*$")
private val hexColor = Regex("#[0-9a-fA-F]{6}")

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
}

internal fun parameterValuesJson(parameters: List<WorkParameter>, values: Map<String, String>): String =
    JSONObject().apply {
        parameters.forEach { parameter ->
            val value = parameterValue(parameter, values[parameter.name])
            when (parameter) {
                is WorkParameter.Number -> put(parameter.name, value.toDouble())
                is WorkParameter.Boolean -> put(parameter.name, value.toBoolean())
                is WorkParameter.Color -> put(parameter.name, value)
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
        }
        if (!replaced || kind.isEmpty()) return@map line
        changed += name
        line.substring(0, range.first) + parts.joinToString(" ") + (if (line.endsWith("\r")) "\r" else "")
    }
    return if (changed.isEmpty()) null else lines.joinToString("\n") to changed
}
