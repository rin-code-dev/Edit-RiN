package com.hikariatelier.app

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

private const val INDEX = "index.json"
private const val BACKUP_INDEX = "index.json.backup"
private const val PENDING_INDEX = "index.json.pending"
private val workDocumentName = Regex("[a-f0-9]{64}-[a-f0-9]{64}\\.json")
private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Immutable saved values detect in-place changes without serializing unchanged code. */
private data class SavedWork(
    val id: String, val title: String, val code: String, val files: Map<String, String>,
    val assets: Map<String, ProjectAsset>, val revisions: List<WorkRevision>,
    val ratio: String, val runtime: String, val sound: Boolean, val libraries: Map<String, String>,
    val parameters: Map<String, String>, val createdAt: Long, val updatedAt: Long,
    val pinned: Boolean, val tags: List<String>, val folder: String
) {
    companion object {
        fun of(work: Work) = SavedWork(work.id, work.title, work.code, work.files.toMap(), work.assets.toMap(),
            work.revisions.takeLast(3), work.previewAspectRatio, work.p5Version, work.p5SoundEnabled,
            work.libraries.toMap(), work.parameterValues.toMap(), work.createdAt, work.updatedAt,
            work.isPinned, work.tags.toList(), work.folderName)
    }
}

private data class WorkEntry(val id: String, val file: String, val metadata: Work, val assetHashes: Set<String>?)
private data class WorkIndex(val json: String, val entries: List<WorkEntry>, val activeId: String, val completeSummaries: Boolean)
private class LoadedWorkIndex(
    val index: WorkIndex, var store: WorkStore, val source: String,
    val primaryText: String?, val saved: MutableMap<String, SavedWork>,
    val deferred: MutableSet<String> = mutableSetOf()
)

private fun decodeIndex(json: String): WorkIndex {
    val root = JSONObject(json)
    require(root.getString("format") == "edit-rin-work-index" && root.getInt("version") in 1..2)
    val array = root.getJSONArray("works")
    val entries = List(array.length()) { i ->
        val item = array.getJSONObject(i)
        val id = item.getString("id")
        val file = item.getString("file")
        require(id.isNotBlank() && workDocumentName.matches(file))
        require(file.substringBefore('-') == sha256(id))
        val metadata = Work(id, item.getString("title"), "", files = item.optJSONArray("fileNames")?.let { names ->
            List(names.length()) { names.getString(it) }.associateWith { "" }.toMutableMap()
        } ?: mutableMapOf(), libraries = item.optJSONObject("libraries")?.let { libraries ->
            libraries.keys().asSequence().associateWith(libraries::getString)
        }.orEmpty(),
            createdAt = item.getLong("createdAt"), updatedAt = item.getLong("updatedAt"),
            folderName = item.optString("folderName", ""),
            isPinned = item.optBoolean("isPinned"), tags = item.optJSONArray("tags")?.let { tags ->
                List(tags.length()) { tags.getString(it) }
            }.orEmpty(), previewAspectRatio = item.optString("previewAspectRatio", "1:1"),
            p5Version = item.optString("p5Version", P5_VERSION_CURRENT),
            p5SoundEnabled = item.optBoolean("p5SoundEnabled")).also { it.bodyLoaded = false }
        val hashes = item.optJSONArray("assetHashes")?.let { hashes ->
            List(hashes.length()) { hashes.getString(it).also { hash -> require(Regex("[a-f0-9]{64}").matches(hash)) } }.toSet()
        }
        WorkEntry(id, file, metadata, hashes)
    }
    require(entries.map { it.id }.toSet().size == entries.size)
    val active = root.getString("activeWorkId")
    require(if (entries.isEmpty()) active.isEmpty() else entries.any { it.id == active })
    val completeSummaries = (0 until array.length()).all { i ->
        val item = array.getJSONObject(i)
        listOf("previewAspectRatio", "p5Version", "p5SoundEnabled", "fileNames", "libraries").all(item::has)
    }
    return WorkIndex(json, entries, active, completeSummaries)
}

private fun encodeIndex(works: List<Work>, entries: List<WorkEntry>, activeId: String): WorkIndex {
    val json = JSONObject().put("format", "edit-rin-work-index").put("version", 2)
        .put("activeWorkId", activeId).put("works", JSONArray().apply {
            works.zip(entries).forEach { (work, entry) ->
                put(JSONObject().put("id", entry.id).put("file", entry.file).put("title", work.title)
                    .put("createdAt", work.createdAt).put("updatedAt", work.updatedAt)
                    .put("folderName", work.folderName)
                    .put("isPinned", work.isPinned).put("tags", JSONArray(work.tags))
                    .put("previewAspectRatio", work.previewAspectRatio).put("p5Version", work.p5Version)
                    .put("p5SoundEnabled", work.p5SoundEnabled).put("fileNames", JSONArray(work.files.keys.sorted()))
                    .put("libraries", JSONObject(work.libraries)).apply {
                        entry.assetHashes?.let { put("assetHashes", JSONArray(it.sorted())) }
                    })
            }
        }).toString()
    return decodeIndex(json)
}

/** Immutable per-work documents + one commit point. All methods must be called on IO. */
internal class SplitWorkStore(private val documents: WorkDocuments) {
    private var loaded: LoadedWorkIndex? = null
    private var baselineKnown = false
    private var conflict = false
    var recovered: Boolean = false
        private set
    val deferredWorkIds: Set<String> get() = loaded?.deferred?.toSet().orEmpty()

    private fun readWork(entry: WorkEntry): Work {
        val text = checkNotNull(documents.read(entry.file)) { "Missing work document" }
        check(sha256(text) == entry.file.substringAfter('-').removeSuffix(".json")) { "Damaged work document" }
        val store = checkNotNull(parseWorkStoreJson(text)) { "Invalid work document" }
        check(store.works.size == 1 && store.works.single().id == entry.id)
        return store.works.single()
    }

    private fun readIndex(index: WorkIndex, source: String, primary: String?, eager: Boolean, selectedId: String?): LoadedWorkIndex {
        val active = selectedId?.takeIf { id -> index.entries.any { it.id == id } } ?: index.activeId
        // Existence checks do not read or parse inactive bodies. Their checksums are checked on first use.
        check(index.entries.all { documents.exists(it.file) }) { "Missing work document" }
        val deferred = mutableSetOf<String>()
        val works = index.entries.map { entry ->
            if (eager || !index.completeSummaries || entry.id == active) readWork(entry) else entry.metadata.also { deferred += entry.id }
        }
        return LoadedWorkIndex(index, WorkStore(works, active), source, primary,
            works.associate { it.id to SavedWork.of(it) }.toMutableMap(), deferred)
    }

    /** Calling load is the explicit acknowledgement of a newer generation after a conflict. */
    @Synchronized
    fun load(eager: Boolean = true, preferredId: String? = null): WorkStore? {
        loaded = null
        baselineKnown = true
        conflict = false
        recovered = false
        documents.refresh()
        if (!documents.exists(INDEX) && !documents.exists(BACKUP_INDEX)) return null
        val failures = mutableListOf<Throwable>()
        val primary = runCatching { documents.read(INDEX) }.onFailure(failures::add).getOrNull()
        for (name in listOf(INDEX, BACKUP_INDEX)) {
            val candidate = runCatching {
                val json = checkNotNull(if (name == INDEX) primary else documents.read(name))
                readIndex(decodeIndex(json), name, primary, eager, preferredId)
            }.onFailure(failures::add).getOrNull()
            if (candidate != null) {
                loaded = candidate
                recovered = name == BACKUP_INDEX
                return candidate.store
            }
        }
        if (failures.any { it is java.io.IOException || it is SecurityException })
            throw java.io.IOException("Cannot access saved work documents", failures.first())
        error("No readable split work store")
    }

    @Synchronized
    fun loadIfNeeded(): WorkStore? = if (baselineKnown) loaded?.store else load()

    @Synchronized
    fun loadWork(id: String): Work? {
        val current = loaded ?: return load()?.works?.find { it.id == id }
        if (id !in current.deferred) return current.store.works.find { it.id == id }
        val entry = current.index.entries.find { it.id == id } ?: return null
        val work = readWork(entry)
        current.store = current.store.copy(works = current.store.works.map { if (it.id == id) work else it })
        current.deferred.remove(id)
        current.saved[id] = SavedWork.of(work)
        return work
    }

    /** Hydrate only placeholders; preserve incoming metadata and any edits already made to loaded works. */
    @Synchronized
    fun materializeWorks(works: List<Work>): List<Work> = works.map { incoming ->
        val current = loaded
        if (incoming.bodyLoaded) return@map incoming
        checkNotNull(current) { "Cannot hydrate a placeholder without its saved generation" }
        val entry = checkNotNull(current.index.entries.find { it.id == incoming.id })
        val base = SavedWork.of(entry.metadata)
        // Do not change the deferred cache here: callers may hydrate both baseline and edits
        // while preparing a three-way merge from this same immutable generation.
        val full = current.store.works.find { it.id == incoming.id && it.bodyLoaded } ?: readWork(entry)
        overlayPlaceholder(incoming, base, full)
    }

    private fun overlayPlaceholder(incoming: Work, base: SavedWork, full: Work): Work = snapshotWork(full).apply {
        title = incoming.title; updatedAt = incoming.updatedAt; isPinned = incoming.isPinned
        folderName = incoming.folderName
        tags.clear(); tags.addAll(incoming.tags)
        if (incoming.previewAspectRatio != base.ratio) previewAspectRatio = incoming.previewAspectRatio
        if (incoming.p5Version != base.runtime) p5Version = incoming.p5Version
        if (incoming.p5SoundEnabled != base.sound) p5SoundEnabled = incoming.p5SoundEnabled
        // A placeholder has empty body fields. Apply only values actually changed relative to it.
        if (incoming.code != base.code) code = incoming.code
        if (incoming.files.toMap() != base.files) { files.clear(); files.putAll(incoming.files) }
        if (incoming.assets.toMap() != base.assets) { assets.clear(); assets.putAll(incoming.assets) }
        if (incoming.revisions.takeLast(3) != base.revisions) { revisions.clear(); revisions.addAll(incoming.revisions) }
        if (incoming.libraries != base.libraries) libraries = incoming.libraries
        if (incoming.parameterValues.toMap() != base.parameters) { parameterValues.clear(); parameterValues.putAll(incoming.parameterValues) }
    }

    @Synchronized
    fun loadOrMigrate(legacy: () -> WorkStore?, prepare: (WorkStore) -> Unit = {},
                      eager: Boolean = true, preferredId: String? = null): WorkStore? {
        load(eager, preferredId)?.let { return it }
        val original = legacy() ?: return null
        prepare(original)
        if (save(original.works, original.activeWorkId)) return checkNotNull(load(eager, preferredId))
        return original
    }

    @Synchronized
    fun worksWithChangedAssets(works: List<Work>): List<Work> = works.filter { work ->
        work.assets.isNotEmpty() && loaded?.saved?.get(work.id)?.assets != work.assets.toMap()
    }

    /** Read both generations conservatively before any asset pruning. */
    @Synchronized
    fun retainedAssetHashes(): Set<String> = buildSet {
        for (name in listOf(INDEX, BACKUP_INDEX)) {
            if (documents.exists(name)) {
                val index = decodeIndex(checkNotNull(documents.read(name)))
                index.entries.forEach { entry ->
                    // Cleanup is infrequent and must verify bodies, not trust editable index summaries.
                    addAll(readWork(entry).assets.values.map { it.hash })
                }
            }
        }
    }

    @Synchronized
    fun save(works: List<Work>, activeId: String): Boolean = saveResult(works, activeId) == WorkSaveResult.Saved

    @Synchronized
    fun saveResult(works: List<Work>, activeId: String): WorkSaveResult {
        if (conflict) return WorkSaveResult.Conflict
        val created = mutableListOf<String>()
        var committed = false
        try {
            require(works.map { it.id }.toSet().size == works.size && works.all { it.id.isNotBlank() })
            require(if (works.isEmpty()) activeId.isEmpty() else works.any { it.id == activeId })
            if (!baselineKnown) load()
            val previous = loaded
            documents.refresh()
            // Keep this expected generation until an explicit reload; a retry cannot acknowledge it.
            if (documents.read(INDEX) != previous?.primaryText ||
                (previous == null && documents.exists(BACKUP_INDEX)) ||
                (previous?.source == BACKUP_INDEX && documents.read(BACKUP_INDEX) != previous.index.json)) {
                conflict = true
                return WorkSaveResult.Conflict
            }
            // A lazy startup has not established that every inactive document is healthy.
            // Verify it once before retiring the last complete backup generation. A failed
            // verification keeps the expected index and all backup files intact for retry.
            previous?.deferred?.toList()?.forEach { checkNotNull(loadWork(it)) }
            val oldBackup = runCatching { documents.read(BACKUP_INDEX)?.let(::decodeIndex) }.getOrNull()
            val oldEntries = previous?.index?.entries.orEmpty().associateBy { it.id }
            val persisted = works.map { work ->
                val saved = previous?.saved?.get(work.id)
                if (!work.bodyLoaded && saved != SavedWork.of(work)) {
                    val entry = checkNotNull(oldEntries[work.id]) { "Placeholder has no saved body" }
                    val full = previous?.store?.works?.find { it.id == work.id && it.bodyLoaded } ?: readWork(entry)
                    overlayPlaceholder(work, SavedWork.of(entry.metadata), full)
                } else work
            }
            val values = persisted.associate { it.id to SavedWork.of(it) }
            val entries = persisted.map { work ->
                val existing = oldEntries[work.id]
                if (existing != null && previous?.saved?.get(work.id) == values[work.id]) existing else {
                    val json = serializeWorkStore(listOf(work), work.id)
                    val name = "${sha256(work.id)}-${sha256(json)}.json"
                    if (documents.exists(name)) {
                        check(documents.read(name) == json) { "Conflicting work document" }
                    } else {
                        created += name
                        check(documents.write(name, json) && documents.read(name) == json)
                    }
                    WorkEntry(work.id, name, work, work.assets.values.map { it.hash }.toSet())
                }
            }
            val next = encodeIndex(persisted, entries, activeId)
            if (previous?.index?.json == next.json) return WorkSaveResult.Saved
            when (commitIndex(next.json, previous)) {
                WorkSaveResult.Conflict -> { conflict = true; return WorkSaveResult.Conflict }
                WorkSaveResult.Failed -> return WorkSaveResult.Failed
                WorkSaveResult.Saved -> Unit
            }
            committed = true
            val stillDeferred = persisted.filterNot { it.bodyLoaded }.map { it.id }.toMutableSet()
            loaded = LoadedWorkIndex(next, WorkStore(persisted, activeId), INDEX, next.json, values.toMutableMap(), stillDeferred)
            recovered = false
            val keep = (entries + previous?.index?.entries.orEmpty()).map { it.file }.toSet()
            oldBackup?.entries?.map { it.file }?.filterNot(keep::contains)?.forEach { runCatching { documents.delete(it) } }
            return WorkSaveResult.Saved
        } catch (_: Exception) {
            // Retain the expected primary text on I/O failures too. Never silently rebase stale callers.
            return WorkSaveResult.Failed
        } finally {
            if (!committed) discardUnreferencedCreated(created)
        }
    }

    /** A provider can fail rollback too. Keep bodies if any index cannot be checked safely. */
    private fun discardUnreferencedCreated(created: List<String>) {
        val referenced = runCatching {
            listOf(INDEX, BACKUP_INDEX, PENDING_INDEX).flatMap { name ->
                if (documents.exists(name)) decodeIndex(checkNotNull(documents.read(name))).entries.map { it.file } else emptyList()
            }.toSet()
        }.getOrNull() ?: return
        created.filterNot(referenced::contains).forEach { runCatching { documents.delete(it) } }
    }

    private fun commitIndex(json: String, previous: LoadedWorkIndex?): WorkSaveResult {
        var promotionAttempted = false
        var promoted = false
        var verified = false
        try {
            if (documents.exists(PENDING_INDEX) && !documents.delete(PENDING_INDEX)) return WorkSaveResult.Failed
            if (!documents.write(PENDING_INDEX, json) || documents.read(PENDING_INDEX) != json) return WorkSaveResult.Failed
            if (documents.read(INDEX) != previous?.primaryText) return WorkSaveResult.Conflict
            if (previous?.source == INDEX) {
                if (documents.exists(BACKUP_INDEX) && !documents.delete(BACKUP_INDEX)) return WorkSaveResult.Failed
                if (!documents.rename(INDEX, BACKUP_INDEX)) return WorkSaveResult.Failed
            } else if (documents.exists(INDEX) && !documents.delete(INDEX)) return WorkSaveResult.Failed
            promotionAttempted = true
            promoted = documents.rename(PENDING_INDEX, INDEX)
            if (!promoted || documents.read(INDEX) != json) return WorkSaveResult.Failed
            verified = true
            return WorkSaveResult.Saved
        } catch (_: Exception) { return WorkSaveResult.Failed }
        finally {
            if (promotionAttempted && !verified) runCatching {
                if (promoted && documents.exists(INDEX)) check(documents.delete(INDEX))
                if (!documents.exists(INDEX) && documents.exists(BACKUP_INDEX)) check(documents.rename(BACKUP_INDEX, INDEX))
            }
            runCatching { if (documents.exists(PENDING_INDEX)) documents.delete(PENDING_INDEX) }
        }
    }
}
