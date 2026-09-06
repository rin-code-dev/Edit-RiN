package com.hikariatelier.app

import org.json.JSONArray
import org.json.JSONObject

internal fun serializeWorkStore(
    works: List<Work>,
    activeWorkId: String
): String {
    val array = JSONArray()
    works.forEach { work ->
        array.put(
            JSONObject()
                .put("id", work.id)
                .put("title", work.title)
                .put("code", work.code)
                .put("files", JSONObject(work.files as Map<*, *>))
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
                .put("createdAt", work.createdAt)
                .put("updatedAt", work.updatedAt)
        )
    }
    return JSONObject()
        .put("format", "hikari-atelier")
        .put("version", 4)
        .put("activeWorkId", activeWorkId)
        .put("savedAt", System.currentTimeMillis())
        .put("works", array)
        .toString(2)
}

internal fun parseWorkStoreJson(json: String): WorkStore? = runCatching {
    val root = JSONObject(json)
    require(!root.has("format") || root.optString("format") == "hikari-atelier")
    // Refuse future formats instead of dropping fields on the next save.
    require(!root.has("version") || root.getInt("version") in 1..4)
    val array = root.getJSONArray("works")
    val result = mutableListOf<Work>()
    val ids = mutableSetOf<String>()
    for (index in 0 until array.length()) {
        val item = array.getJSONObject(index)
        val id = item.getString("id")
        require(id.isNotBlank() && ids.add(id)) { "作品IDが空、または重複しています" }
        require(!item.has("files") || item.get("files") is JSONObject)
        require(!item.has("revisions") || item.get("revisions") is JSONArray)
        val now = System.currentTimeMillis()
        result += Work(
            id = id,
            title = item.getString("title"),
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
