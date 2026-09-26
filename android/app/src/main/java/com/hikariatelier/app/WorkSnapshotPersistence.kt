package com.hikariatelier.app

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val MAX_SNAPSHOTS = 15

/** Returns on the caller's UI context; failed IO never invokes the session mutation. */
internal suspend fun persistSnapshotRestore(save: () -> Boolean, commit: () -> Unit): Boolean {
    if (!withContext(Dispatchers.IO) { save() }) return false
    commit()
    return true
}

data class WorkSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val savedAt: Long = System.currentTimeMillis(),
    val code: String,
    val files: Map<String, String> = emptyMap(),
    val parameterValues: Map<String, String> = emptyMap(),
    val note: String? = null,
    // Legacy revisions only recorded sketch.js; do not erase other files when restoring them.
    val mainCodeOnly: Boolean = false
)

internal data class SnapshotContent(
    val code: String,
    val files: Map<String, String>,
    val parameterValues: Map<String, String>
)

internal fun currentSnapshotContent(work: Work, code: String, drafts: Map<String, String>) = SnapshotContent(
    code,
    work.files.mapValues { (name, saved) -> drafts["${work.id}/$name"] ?: saved },
    work.parameterValues.toMap()
)

internal fun WorkSnapshot.matches(current: SnapshotContent): Boolean = code == current.code &&
    (mainCodeOnly || (files == current.files && parameterValues == current.parameterValues))

internal fun restoredSnapshotWork(work: Work, snapshot: WorkSnapshot, current: SnapshotContent): Work =
    snapshotWork(work).also { restored ->
        restored.code = snapshot.code
        restored.files.clear()
        restored.files.putAll(if (snapshot.mainCodeOnly) current.files else snapshot.files)
        restored.parameterValues.clear()
        restored.parameterValues.putAll(if (snapshot.mainCodeOnly) current.parameterValues else snapshot.parameterValues)
        restored.updatedAt = System.currentTimeMillis()
    }

internal fun snapshotDifference(current: SnapshotContent, target: WorkSnapshot): String = buildString {
    if (current.code != target.code) {
        appendLine("sketch.js")
        appendLine(revisionDifference(current.code, target.code))
    }
    if (!target.mainCodeOnly) {
        (current.files.keys + target.files.keys).sorted().forEach { name ->
            if (current.files[name] != target.files[name]) {
                appendLine("${if (name !in current.files) "+ " else if (name !in target.files) "− " else ""}$name")
                appendLine(revisionDifference(current.files[name].orEmpty(), target.files[name].orEmpty()))
            }
        }
        (current.parameterValues.keys + target.parameterValues.keys).sorted().forEach { name ->
            if (current.parameterValues[name] != target.parameterValues[name]) {
                appendLine("rinParams.$name")
                appendLine("− ${current.parameterValues[name] ?: "∅"}")
                appendLine("+ ${target.parameterValues[name] ?: "∅"}")
            }
        }
    }
}

internal fun encodeSnapshots(snapshots: List<WorkSnapshot>): JSONArray = JSONArray().apply {
    require(snapshots.size <= MAX_SNAPSHOTS)
    snapshots.forEach { snapshot ->
        put(JSONObject().put("id", snapshot.id).put("savedAt", snapshot.savedAt)
            .put("code", snapshot.code).put("files", JSONObject(snapshot.files))
            .put("parameterValues", JSONObject(snapshot.parameterValues))
            .put("mainCodeOnly", snapshot.mainCodeOnly).apply {
                snapshot.note?.let { put("note", it) }
            })
    }
}

internal fun decodeSnapshots(array: JSONArray): List<WorkSnapshot> {
    require(array.length() <= MAX_SNAPSHOTS) { "Too many snapshots" }
    val ids = mutableSetOf<String>()
    fun stringMap(obj: JSONObject?, present: Boolean): Map<String, String> {
        require(!present || obj != null) { "Invalid snapshot map" }
        return obj?.let { value -> value.keys().asSequence().associateWith { key ->
            require(value.get(key) is String)
            value.getString(key)
        } }.orEmpty()
    }
    return (0 until array.length()).map { index ->
        val obj = array.getJSONObject(index)
        val id = obj.optString("id", index.toString())
        require(id.isNotBlank() && ids.add(id)) { "Duplicate snapshot ID" }
        require(obj.get("code") is String)
        val files = stringMap(obj.optJSONObject("files"), obj.has("files"))
        val params = stringMap(obj.optJSONObject("parameterValues"), obj.has("parameterValues"))
        WorkSnapshot(id, obj.optLong("savedAt", 0), obj.getString("code"), files, params,
            obj.optString("note").takeIf { it.isNotBlank() },
            obj.optBoolean("mainCodeOnly", id.startsWith("migrated_") && files.isEmpty() && params.isEmpty()))
    }.sortedByDescending { it.savedAt }
}

/** A failed or interrupted write never truncates the previous JSON. Also usable in JVM tests. */
internal class SnapshotAtomicFile(private val base: File) {
    private val backup = File(base.path + ".bak")
    private val pending = File(base.path + ".new")

    private fun recover() {
        if (backup.exists()) {
            check(!base.exists() || base.delete()) { "Cannot recover snapshot" }
            check(backup.renameTo(base)) { "Cannot recover snapshot" }
        }
    }

    fun read(): String? {
        recover()
        return if (base.exists()) base.readText() else null
    }

    fun write(text: String) {
        check(base.parentFile!!.isDirectory || base.parentFile!!.mkdirs())
        recover() // Do not allocate/read the old JSON again just to write the pending copy.
        try {
            FileOutputStream(pending).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (base.exists()) check(base.renameTo(backup)) { "Cannot retain old snapshots" }
            check(pending.renameTo(base)) { "Cannot commit snapshots" }
            check(!backup.exists() || backup.delete()) { "Cannot finish snapshot commit" }
        } catch (error: Exception) {
            if (backup.exists()) {
                if (base.exists()) check(base.delete())
                check(backup.renameTo(base))
            }
            throw error
        } finally {
            if (pending.isFile) pending.delete()
        }
    }
}

/** Call from IO. Serialization covers read-modify-write as well as imports and exports. */
internal object WorkSnapshotStore {
    private fun snapshotFile(filesDir: File, workId: String): File {
        require(workId.isNotBlank())
        // Keep existing safe filenames (including imported IDs with spaces/non-ASCII characters).
        val safeName = workId.length <= 180 && workId !in setOf(".", "..") &&
            workId.none { it == '/' || it == '\\' || it.isISOControl() }
        val name = if (safeName) workId else
            "id-" + MessageDigest.getInstance("SHA-256").digest(workId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        return File(File(filesDir, "snapshots"), "$name.json")
    }

    @Synchronized
    fun loadSnapshots(filesDir: File, workId: String, fallbackRevisions: List<WorkRevision> = emptyList()): List<WorkSnapshot> {
        val file = SnapshotAtomicFile(snapshotFile(filesDir, workId))
        file.read()?.let { return decodeSnapshots(JSONArray(it)) }
        // Migration is persisted on the first mutation, not on opening/switching works.
        return fallbackRevisions.takeLast(10).mapIndexed { index, revision ->
            WorkSnapshot(id = "migrated_$index", savedAt = revision.savedAt, code = revision.code, mainCodeOnly = true)
        }.sortedByDescending { it.savedAt }
    }

    @Synchronized
    fun ensureLegacyMigrated(filesDir: File, workId: String, revisions: List<WorkRevision>) {
        if (revisions.isEmpty()) return
        val file = snapshotFile(filesDir, workId)
        if (file.exists() || File(file.path + ".bak").exists()) return
        val migrated = loadSnapshots(filesDir, workId, revisions)
        SnapshotAtomicFile(file).write(encodeSnapshots(migrated).toString())
    }

    @Synchronized
    fun saveSnapshots(filesDir: File, workId: String, snapshots: List<WorkSnapshot>) {
        val file = SnapshotAtomicFile(snapshotFile(filesDir, workId))
        // Refuse to silently replace corrupt history.
        file.read()?.let { decodeSnapshots(JSONArray(it)) }
        file.write(encodeSnapshots(snapshots.take(MAX_SNAPSHOTS)).toString())
    }

    @Synchronized
    fun addSnapshot(filesDir: File, workId: String, content: SnapshotContent,
                    fallbackRevisions: List<WorkRevision> = emptyList()): List<WorkSnapshot> {
        val current = loadSnapshots(filesDir, workId, fallbackRevisions)
        val updated = (listOf(WorkSnapshot(code = content.code, files = content.files,
            parameterValues = content.parameterValues)) + current).take(MAX_SNAPSHOTS)
        saveSnapshots(filesDir, workId, updated)
        return updated
    }

    @Synchronized
    fun deleteSnapshot(filesDir: File, workId: String, snapshotId: String,
                       fallbackRevisions: List<WorkRevision> = emptyList()): List<WorkSnapshot> {
        val updated = loadSnapshots(filesDir, workId, fallbackRevisions).filterNot { it.id == snapshotId }
        saveSnapshots(filesDir, workId, updated)
        return updated
    }

    @Synchronized
    fun exportSnapshots(filesDir: File, works: List<Work>): Map<String, List<WorkSnapshot>> =
        works.associate { it.id to loadSnapshots(filesDir, it.id, it.revisions.toList()) }

    /** Restore histories before publishing works, and roll them back if work persistence fails. */
    @Synchronized
    fun importWithWorks(filesDir: File, snapshots: Map<String, List<WorkSnapshot>>,
                        saveWorks: () -> Boolean): Boolean {
        val previous = snapshots.keys.associateWith { id -> SnapshotAtomicFile(snapshotFile(filesDir, id)).read() }
        val changed = mutableListOf<String>()
        try {
            snapshots.forEach { (id, history) ->
                SnapshotAtomicFile(snapshotFile(filesDir, id)).write(encodeSnapshots(history).toString())
                changed += id
            }
            check(saveWorks()) { "Cannot save imported works" }
            return true
        } catch (error: Exception) {
            changed.asReversed().forEach { id ->
                val old = previous[id]
                if (old != null) SnapshotAtomicFile(snapshotFile(filesDir, id)).write(old)
                else check(snapshotFile(filesDir, id).delete())
            }
            throw error
        }
    }
}
