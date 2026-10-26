package com.hikariatelier.app

import org.json.JSONObject

/** Offsets use UTF-16 code units, matching Kotlin strings and JavaScript parser ranges. */
internal data class SourcePatch(val start: Int, val end: Int, val text: String)

/** A block action is accepted only against the exact source it was prepared from. */
internal data class BlockEditTransaction(
    val workId: String,
    val expectedSource: String,
    val patches: List<SourcePatch>,
    val blockEditorState: String?,
    val expectedRevision: String? = null
)

/** Validate every range before producing output; ambiguous coincident patches are rejected. */
internal fun applySourcePatches(source: String, patches: List<SourcePatch>): String? {
    val ordered = patches.sortedWith(compareBy<SourcePatch> { it.start }.thenBy { it.end })
    var previousEnd = 0
    var previousStart = -1
    for (patch in ordered) {
        if (patch.start < 0 || patch.end < patch.start || patch.end > source.length ||
            patch.start < previousEnd || patch.start == previousStart) return null
        // Do not split a surrogate pair when applying an external parser's ranges.
        fun splitsPair(offset: Int): Boolean = offset > 0 && offset < source.length &&
            Character.isHighSurrogate(source[offset - 1]) && Character.isLowSurrogate(source[offset])
        if (splitsPair(patch.start) || splitsPair(patch.end)) return null
        previousStart = patch.start
        previousEnd = patch.end
    }
    return buildString {
        var cursor = 0
        ordered.forEach { patch ->
            append(source, cursor, patch.start)
            append(patch.text)
            cursor = patch.end
        }
        append(source, cursor, source.length)
    }
}

/** Production bridge messages carry both a document revision and their own ACK correlation ID. */
internal fun decodeBlockEditTransaction(workId: String, message: JSONObject): BlockEditTransaction? = runCatching {
    val revision = message.get("expectedRevision")
    val requestId = message.get("requestId")
    require(revision is String && revision.isNotBlank())
    require(requestId is String && requestId.isNotBlank())
    require(message.get("expectedSource") is String)
    require(message.has("metadata") && (message.isNull("metadata") || message.get("metadata") is String))
    val patches = message.getJSONArray("patches")
    require(patches.length() <= 10_000)
    BlockEditTransaction(workId, message.getString("expectedSource"), List(patches.length()) { index ->
        val patch = patches.getJSONObject(index)
        require(patch.get("start") is Number && patch.get("end") is Number && patch.get("text") is String)
        val start = patch.getDouble("start")
        val end = patch.getDouble("end")
        require(start.isFinite() && end.isFinite() && start == start.toInt().toDouble() && end == end.toInt().toDouble())
        SourcePatch(start.toInt(), end.toInt(), patch.getString("text"))
    }, if (message.isNull("metadata")) null else message.getString("metadata"), revision)
}.getOrNull()

internal fun blockEditAckMessage(sessionId: String, accepted: Boolean, workId: String, source: String,
                                metadata: String?, revision: String, requestId: String? = null,
                                expectedRevision: String? = null): JSONObject =
    JSONObject().put("type", "ack").put("sessionId", sessionId).put("workId", workId)
        .put("accepted", accepted).put("source", source).put("revision", revision)
        .put("metadata", metadata ?: JSONObject.NULL).apply {
            requestId?.let { put("requestId", it) }
            expectedRevision?.let { put("expectedRevision", it) }
        }
