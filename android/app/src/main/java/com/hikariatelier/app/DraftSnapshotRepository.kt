package com.hikariatelier.app

import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal fun draftStoreKey(folderUri: Uri?): String = folderUri?.toString() ?: "local"

/** Length-prefixed, deterministic authored content; metadata-only updates do not invalidate edits. */
internal fun draftBaseHash(work: Work): String = draftBaseHash(work.code, work.files.toMap())

internal fun draftBaseHash(code: String, files: Map<String, String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun add(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
        digest.update(bytes)
    }
    add(code)
    files.toSortedMap().forEach { (name, code) -> add(name); add(code) }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** One writer serializes drafts/deletion; queued obsolete saves are replaced by the latest command. */
internal class DraftSnapshotRepository(private val file: File) {
    private val writer = Executors.newSingleThreadExecutor()
    private val queueLock = Any()
    private var pending: (() -> Unit)? = null
    private var draining = false
    private var generation = 0L

    /** Reserve before hashing; validation and enqueue share the same lock as clear. */
    fun beginGeneration(): Long = synchronized(queueLock) { ++generation }
    fun compute(generationToken: Long, action: () -> Unit) { enqueue(generationToken, action) }

    @Synchronized
    fun load(): Pair<DraftSnapshot, Map<String, String>>? = runCatching {
        val text = SnapshotAtomicFile(file).read() ?: return null
        val root = JSONObject(text)
        val files = root.optJSONObject("fileDrafts")?.let { json ->
            json.keys().asSequence().associateWith(json::getString)
        }.orEmpty()
        val bases = root.optJSONObject("bases")?.let { json ->
            json.keys().asSequence().associateWith(json::getString)
        }.orEmpty()
        DraftSnapshot(root.optString("workId"), root.optString("code"), root.optLong("updatedAt"),
            root.optString("storeKey").takeIf { it.isNotEmpty() },
            root.optString("baseHash").takeIf { it.isNotEmpty() }, bases) to files
    }.getOrNull()

    /** Unknown legacy/cross-folder/stale drafts stay on disk for explicit recovery. */
    fun loadCompatible(storeKey: String, works: List<Work>): Pair<DraftSnapshot, Map<String, String>>? {
        val raw = load() ?: return null
        val snapshot = raw.first
        if (snapshot.storeKey != storeKey) return null
        val active = works.find { it.id == snapshot.workId } ?: return null
        if (snapshot.baseHash != draftBaseHash(active)) return null
        val compatibleFiles = raw.second.filter { (key, _) ->
            // IDs may contain '/'; use actual work IDs instead of splitting an untrusted key.
            val work = works.firstOrNull { key.startsWith("${it.id}/") && key.removePrefix("${it.id}/") in it.files }
            work != null && (snapshot.bases[work.id] ?: snapshot.baseHash.takeIf { work.id == snapshot.workId }) == draftBaseHash(work)
        }
        return snapshot to compatibleFiles
    }

    fun save(workId: String, code: String, files: Map<String, String>, storeKey: String? = null,
             baseHash: String? = null, bases: Map<String, String> = emptyMap(),
             generationToken: Long = beginGeneration()) {
        if (workId.isBlank()) return
        val capturedFiles = files.toMap()
        val capturedBases = bases.toMap()
        val updatedAt = System.currentTimeMillis()
        enqueue(generationToken) {
            runCatching {
                val json = JSONObject().put("version", 2).put("workId", workId).put("code", code)
                    .put("fileDrafts", JSONObject(capturedFiles)).put("updatedAt", updatedAt)
                    .put("storeKey", storeKey).put("baseHash", baseHash).put("bases", JSONObject(capturedBases)).toString()
                synchronized(this) { SnapshotAtomicFile(file).write(json) }
            }.onFailure { Log.w("EditKIRO", "Draft save failed", it) }
        }
    }

    private fun enqueue(token: Long, command: () -> Unit) = synchronized(queueLock) {
        if (token != generation) return@synchronized
        pending = command
        if (!draining) {
            draining = true
            writer.execute {
                while (true) {
                    val next = synchronized(queueLock) {
                        val captured = pending
                        pending = null
                        if (captured == null) draining = false
                        captured
                    } ?: break
                    runCatching(next).onFailure { Log.w("EditKIRO", "Draft command failed", it) }
                }
            }
        }
    }

    fun clear() = synchronized(queueLock) { enqueue(++generation) {
        synchronized(this) {
            listOf(file, File(file.path + ".bak"), File(file.path + ".new")).forEach {
                check(!it.exists() || it.delete()) { "Cannot remove draft" }
            }
        }
    } }
    internal fun awaitPendingWrites() { writer.submit {}.get(5, TimeUnit.SECONDS) }
    fun close() { writer.shutdown() }
}
