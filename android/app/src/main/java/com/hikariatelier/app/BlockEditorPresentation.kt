package com.hikariatelier.app

import android.content.Context
import org.json.JSONObject

/** View-only state: excluded from source, snapshots, block metadata and undo history. */
internal data class BlockEditorViewport(val x: Double, val y: Double, val scale: Double, val selected: String?) {
    fun toJson(): JSONObject = JSONObject().put("x", x).put("y", y).put("scale", scale)
        .put("selected", selected ?: JSONObject.NULL)
}

internal fun decodeBlockEditorViewport(value: JSONObject?): BlockEditorViewport? {
    if (value == null || listOf("x", "y", "scale").any { value.opt(it) !is Number }) return null
    val x = value.optDouble("x", Double.NaN)
    val y = value.optDouble("y", Double.NaN)
    val scale = value.optDouble("scale", Double.NaN)
    val selected = value.opt("selected").takeUnless { it == JSONObject.NULL }
    if (!x.isFinite() || !y.isFinite() || !scale.isFinite() ||
        kotlin.math.abs(x) > 1e7 || kotlin.math.abs(y) > 1e7 || scale !in 0.3..2.0 ||
        (selected != null && (selected !is String || selected.length > 256))) return null
    return BlockEditorViewport(x, y, scale, selected as? String)
}

internal data class BlockEditorDisplayPreferences(val fontSize: Double = 12.5, val density: String = "standard") {
    fun toJson(): JSONObject = JSONObject().put("fontSize", fontSize).put("density", density)
}

internal fun decodeBlockEditorDisplayPreferences(value: JSONObject?): BlockEditorDisplayPreferences? {
    if (value == null || value.opt("fontSize") !is Number || value.opt("density") !is String) return null
    val size = value.optDouble("fontSize", Double.NaN)
    val density = value.optString("density")
    if (!size.isFinite() || size !in 12.0..22.0 || density !in setOf("standard", "comfortable")) return null
    return BlockEditorDisplayPreferences(size, density)
}

internal class BlockEditorPresentationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("block_editor_presentation", Context.MODE_PRIVATE)
    fun easyMode(): Boolean = runCatching { preferences.getBoolean("easy_mode", false) }.getOrDefault(false)
    fun saveEasyMode(value: Boolean) {
        preferences.edit().apply {
            putBoolean("easy_mode", value)
            if (value) putBoolean("easy_mode_hint_dismissed", true)
        }.apply()
    }
    fun easyModeHintDismissed(): Boolean =
        runCatching { preferences.getBoolean("easy_mode_hint_dismissed", false) }.getOrDefault(false)
    fun dismissEasyModeHint() {
        preferences.edit().putBoolean("easy_mode_hint_dismissed", true).apply()
    }
    fun displayPreferences(): BlockEditorDisplayPreferences = runCatching {
        decodeBlockEditorDisplayPreferences(JSONObject(preferences.getString("display", "{}").orEmpty()))
    }.getOrNull() ?: BlockEditorDisplayPreferences()
    fun saveDisplayPreferences(value: BlockEditorDisplayPreferences) {
        preferences.edit().putString("display", value.toJson().toString()).apply()
    }
    fun viewport(workId: String): BlockEditorViewport? = runCatching {
        decodeBlockEditorViewport(JSONObject(preferences.getString("view:$workId", "{}").orEmpty()))
    }.getOrNull()
    fun saveViewport(workId: String, value: BlockEditorViewport) {
        preferences.edit().putString("view:$workId", value.toJson().toString()).apply()
    }
}
