package com.hikariatelier.app

import android.util.AtomicFile
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** A single writer owns draft writes and deletion for the whole editor session. */
internal class DraftSnapshotRepository(private val file: File) {
    private val writer = Executors.newSingleThreadExecutor()
    fun load(): Pair<DraftSnapshot, Map<String, String>>? = runCatching {
        val atomic = AtomicFile(file)
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        val root = atomic.openRead().bufferedReader().use { JSONObject(it.readText()) }
        val files = root.optJSONObject("fileDrafts")?.let { json ->
            json.keys().asSequence().associateWith(json::getString)
        }.orEmpty()
        DraftSnapshot(root.optString("workId"), root.optString("code"), root.optLong("updatedAt")) to files
    }.getOrNull()

    fun save(workId: String, code: String, files: Map<String, String>) {
        if (workId.isBlank()) return
        val capturedFiles = files.toMap()
        val updatedAt = System.currentTimeMillis()
        writer.execute {
            runCatching {
                val json = JSONObject().put("workId", workId).put("code", code)
                    .put("fileDrafts", JSONObject(capturedFiles)).put("updatedAt", updatedAt).toString()
                val atomic = AtomicFile(file)
                val output = atomic.startWrite()
                try {
                    output.write(json.toByteArray(Charsets.UTF_8))
                    atomic.finishWrite(output)
                } catch (error: Exception) {
                    atomic.failWrite(output)
                    throw error
                }
            }.onFailure { Log.w("EditKIRO", "Draft save failed", it) }
        }
    }

    fun clear() { writer.execute { AtomicFile(file).delete() } }
    fun close() { writer.shutdown() }
}
