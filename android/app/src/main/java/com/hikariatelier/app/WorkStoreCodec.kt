package com.hikariatelier.app

import org.json.JSONArray
import org.json.JSONObject

internal fun serializeWorkJson(work: Work): JSONObject {
    validateAssetSet(work.assets)
    return JSONObject()
        .put("id", work.id)
        .put("title", work.title)
        .put("code", work.code)
        .apply { work.blockEditorState?.let { put("blockEditorState", it) } }
        .put("files", JSONObject(work.files as Map<*, *>))
        .put("assets", JSONObject().apply {
            work.assets.forEach { (name, asset) ->
                put(name, JSONObject().put("hash", asset.hash).put("size", asset.size).put("mime", asset.mime))
            }
        })
        .put(
            "revisions",
            JSONArray().apply {
                work.revisions.takeLast(3).forEach { revision ->
                    put(
                        JSONObject()
                            .put("code", revision.code)
                            .put("savedAt", revision.savedAt)
                    )
                }
            }
        )
        .put("previewAspectRatio", work.previewAspectRatio)
        .put("p5Version", work.p5Version)
        .put("p5SoundEnabled", work.p5SoundEnabled)
        .put("libraries", JSONObject(work.libraries))
        .put("parameterValues", JSONObject(work.parameterValues as Map<*, *>))
        .apply {
            val presets = normalizeParameterPresets(work.parameterPresets)
            if (presets.isNotEmpty()) {
                put("parameterPresets", JSONArray().apply {
                    presets.forEach { preset ->
                        put(
                            JSONObject()
                                .put("id", preset.id)
                                .put("name", preset.name)
                                .put("values", JSONObject(preset.values as Map<*, *>))
                        )
                    }
                })
            }
        }
        .put("createdAt", work.createdAt)
        .put("updatedAt", work.updatedAt)
        .put("folderName", work.folderName)
        .put("isPinned", work.isPinned)
        .put("tags", JSONArray(work.tags))
}

internal fun serializeWorkStore(
    works: List<Work>,
    activeWorkId: String
): String {
    val array = JSONArray()
    works.forEach { work ->
        array.put(serializeWorkJson(work))
    }
    return JSONObject()
        .put("format", "hikari-atelier")
        .put("version", 6)
        .put("activeWorkId", activeWorkId)
        .put("savedAt", System.currentTimeMillis())
        .put("works", array)
        .toString()
}

internal fun parseWorkStoreJson(json: String): WorkStore? = runCatching {
    val root = JSONObject(json)
    require(!root.has("format") || root.optString("format") == "hikari-atelier")
    // Later formats may add optional fields. Read every field this build understands.
    require(!root.has("version") || root.getInt("version") >= 1)
    val storedVersion = root.optInt("version", 1)
    val array = root.getJSONArray("works")
    val result = mutableListOf<Work>()
    val ids = mutableSetOf<String>()
    for (index in 0 until array.length()) {
        val item = array.getJSONObject(index)
        val id = item.getString("id")
        require(id.isNotBlank() && ids.add(id)) { "作品IDが空、または重複しています" }
        require(!item.has("files") || item.get("files") is JSONObject)
        require(!item.has("blockEditorState") || item.isNull("blockEditorState") || item.get("blockEditorState") is String)
        require(!item.has("revisions") || item.get("revisions") is JSONArray)
        require(!item.has("assets") || item.get("assets") is JSONObject)
        require(!item.has("parameterPresets") || item.isNull("parameterPresets") || item.get("parameterPresets") is JSONArray)
        val assetMap = item.optJSONObject("assets")?.let { assets ->
            assets.keys().asSequence().associateWith { name ->
                val asset = assets.getJSONObject(name)
                ProjectAsset(asset.getString("hash"), asset.getLong("size"), asset.getString("mime"))
            }
        } ?: emptyMap()
        validateAssetSet(assetMap)
        val now = System.currentTimeMillis()
        val work = Work(
            id = id,
            title = item.getString("title"),
            assets = assetMap,
            code = item.getString("code"),
            blockEditorState = if (item.has("blockEditorState") && !item.isNull("blockEditorState")) item.getString("blockEditorState") else null,
            files = item.optJSONObject("files")?.let { files ->
                files.keys().asSequence().associateWith { files.getString(it) }.toMutableMap()
            } ?: mutableMapOf(),
            revisions = item.optJSONArray("revisions")?.let { revisions ->
                MutableList(minOf(revisions.length(), 30)) { revisionIndex ->
                    val revision = revisions.getJSONObject((revisions.length() - 30).coerceAtLeast(0) + revisionIndex)
                    WorkRevision(
                        code = revision.getString("code"),
                        savedAt = revision.optLong("savedAt", now)
                    )
                }
            } ?: mutableListOf(),
            previewAspectRatio = normalizedPreviewAspectRatio(
                item.optString("previewAspectRatio", "1:1")
            ),
            p5Version = if (storedVersion >= 6) {
                normalizedP5Version(item.optString("p5Version", P5_VERSION_LEGACY))
            } else {
                P5_VERSION_LEGACY
            },
            p5SoundEnabled = item.optBoolean("p5SoundEnabled", false),
            libraries = item.optJSONObject("libraries")?.let { libs ->
                libs.keys().asSequence().associateWith { libs.optString(it) }
            }.orEmpty(),
            parameterValues = item.optJSONObject("parameterValues")?.let { values ->
                values.keys().asSequence().take(16).associateWith { values.optString(it) }
            }.orEmpty(),
            parameterPresets = item.optJSONArray("parameterPresets")?.let { presetsArray ->
                normalizeParameterPresets(
                    List(minOf(presetsArray.length(), MAX_PARAMETER_PRESETS)) { presetIndex ->
                        val presetObj = presetsArray.optJSONObject(presetIndex)
                        val presetId = presetObj?.optString("id").orEmpty()
                        val presetName = presetObj?.optString("name").orEmpty()
                        val presetValues = presetObj?.optJSONObject("values")?.let { mapObj ->
                            mapObj.keys().asSequence().take(16).associateWith { mapObj.optString(it) }
                        }.orEmpty()
                        WorkParameterPreset(id = presetId, name = presetName, values = presetValues)
                    }
                )
            }.orEmpty(),
            createdAt = item.optLong("createdAt", now),
            updatedAt = item.optLong("updatedAt", now),
            folderName = item.optString("folderName", ""),
            isPinned = item.optBoolean("isPinned", false),
            tags = item.optJSONArray("tags")?.let { tagsArray ->
                List(tagsArray.length()) { tagsArray.optString(it) }.filter { it.isNotBlank() }
            } ?: emptyList()
        )
        result += work
    }
    WorkStore(
        works = result,
        activeWorkId = root.optString("activeWorkId").takeIf { it in ids }
            ?: result.firstOrNull()?.id.orEmpty()
    )
}.getOrNull()
