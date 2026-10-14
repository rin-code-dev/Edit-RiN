package com.hikariatelier.app

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

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
    files.toSortedMap().forEach { (name, content) -> add(name); add(content) }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Conflated single-lane coroutine writer serializes drafts/deletion; obsolete saves are coalesced automatically. */
internal class DraftSnapshotRepository(private val file: File) {
    private val serialDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val writerScope = CoroutineScope(SupervisorJob() + serialDispatcher)
    private val conflatedTasks = Channel<() -> Unit>(Channel.CONFLATED)
    private val generationLock = Any()
    private var generation = 0L

    init {
        writerScope.launch {
            for (task in conflatedTasks) {
                runCatching(task).onFailure { Log.w(LOG_TAG, "Draft command failed", it) }
            }
        }
    }

    /** Reserve before hashing; validation and enqueue share the same lock as clear. */
    fun beginGeneration(): Long = synchronized(generationLock) { ++generation }
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
        DraftSnapshot(
            root.optString("workId"),
            root.optString("code"),
            root.optLong("updatedAt"),
            root.optString("storeKey").takeIf { it.isNotEmpty() },
            root.optString("baseHash").takeIf { it.isNotEmpty() },
            bases
        ) to files
    }.getOrNull()

    /** Unknown legacy/cross-folder/stale drafts stay on disk for explicit recovery. */
    fun loadCompatible(storeKey: String, works: List<Work>): Pair<DraftSnapshot, Map<String, String>>? {
        val raw = load() ?: return null
        val snapshot = raw.first
        if (snapshot.storeKey != storeKey) return null
        val active = works.find { it.id == snapshot.workId } ?: return null
        if (snapshot.baseHash != draftBaseHash(active)) return null
        val compatibleFiles = raw.second.filter { (key, _) ->
            val work = works.firstOrNull { key.startsWith("${it.id}/") && key.removePrefix("${it.id}/") in it.files }
            work != null && (snapshot.bases[work.id] ?: snapshot.baseHash.takeIf { work.id == snapshot.workId }) == draftBaseHash(work)
        }
        return snapshot to compatibleFiles
    }

    fun save(
        workId: String,
        code: String,
        files: Map<String, String>,
        storeKey: String? = null,
        baseHash: String? = null,
        bases: Map<String, String> = emptyMap(),
        generationToken: Long = beginGeneration()
    ) {
        if (workId.isBlank()) return
        val capturedFiles = files.toMap()
        val capturedBases = bases.toMap()
        val updatedAt = System.currentTimeMillis()
        enqueue(generationToken) {
            runCatching {
                val json = JSONObject()
                    .put("version", 2)
                    .put("workId", workId)
                    .put("code", code)
                    .put("fileDrafts", JSONObject(capturedFiles))
                    .put("updatedAt", updatedAt)
                    .put("storeKey", storeKey)
                    .put("baseHash", baseHash)
                    .put("bases", JSONObject(capturedBases))
                    .toString()
                synchronized(this) { SnapshotAtomicFile(file).write(json) }
            }.onFailure { Log.w(LOG_TAG, "Draft save failed", it) }
        }
    }

    private fun enqueue(token: Long, command: () -> Unit) = synchronized(generationLock) {
        if (token != generation) return@synchronized
        conflatedTasks.trySend(command)
    }

    fun clear() = synchronized(generationLock) {
        enqueue(++generation) {
            synchronized(this) {
                listOf(file, File(file.path + ".bak"), File(file.path + ".new")).forEach {
                    check(!it.exists() || it.delete()) { "Cannot remove draft" }
                }
            }
        }
    }

    internal fun awaitPendingWrites() {
        runBlocking {
            withTimeout(5_000) {
                // Drain any currently conflated command on the same single-lane dispatcher.
                val barrier = CompletableDeferred<Unit>()
                writerScope.launch {
                    while (true) {
                        val next = conflatedTasks.tryReceive().getOrNull() ?: break
                        runCatching(next).onFailure { Log.w(LOG_TAG, "Draft command failed", it) }
                    }
                    barrier.complete(Unit)
                }
                barrier.await()
            }
        }
    }

    fun close() {
        conflatedTasks.close()
        writerScope.cancel()
    }
}
