package com.hikariatelier.app

import org.json.JSONArray
import org.json.JSONObject

internal fun serializeWorkStore(
    works: List<Work>,
    activeWorkId: String
): String {
    val array = JSONArray()
    works.forEach { work ->
        validateAssetSet(work.assets)
        array.put(
            JSONObject()
                .put("id", work.id)
                .put("title", work.title)
                .put("code", work.code)
                .put("files", JSONObject(work.files as Map<*, *>))
                .put("assets", JSONObject().apply {
                    work.assets.forEach { (name, asset) ->
                        put(name, JSONObject().put("hash", asset.hash).put("size", asset.size).put("mime", asset.mime))
                    }
                })
                .put(
                    "revisions",
                    JSONArray().apply {
                        work.revisions.takeLast(30).forEach { revision ->
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
                .put("createdAt", work.createdAt)
                .put("updatedAt", work.updatedAt)
        )
    }
    return JSONObject()
        .put("format", "hikari-atelier")
        .put("version", 6)
        .put("activeWorkId", activeWorkId)
        .put("savedAt", System.currentTimeMillis())
        .put("works", array)
        .toString(2)
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
        require(!item.has("revisions") || item.get("revisions") is JSONArray)
        require(!item.has("assets") || item.get("assets") is JSONObject)
        val assetMap = item.optJSONObject("assets")?.let { assets ->
            assets.keys().asSequence().associateWith { name ->
                val asset = assets.getJSONObject(name)
                ProjectAsset(asset.getString("hash"), asset.getLong("size"), asset.getString("mime"))
            }
        } ?: emptyMap()
        validateAssetSet(assetMap)
        val now = System.currentTimeMillis()
        result += Work(
            id = id,
            title = item.getString("title"),
            assets = assetMap,
            code = item.getString("code"),
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
            createdAt = item.optLong("createdAt", now),
            updatedAt = item.optLong("updatedAt", now)
        )
    }
    WorkStore(
        works = result,
        activeWorkId = root.optString("activeWorkId").takeIf { it in ids }
            ?: result.firstOrNull()?.id.orEmpty()
    )
}.getOrNull()
