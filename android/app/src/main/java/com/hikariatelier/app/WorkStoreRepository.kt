package com.hikariatelier.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

internal interface WorkPersistence {
    fun loadLocal(): WorkStore?
    fun loadFolder(folderUri: Uri): WorkStore?
    fun save(folderUri: Uri?, works: List<Work>, activeId: String): Boolean
    fun selectedWorkId(folderUri: Uri?): String? = null
    fun rememberSelectedWork(folderUri: Uri?, activeId: String) {}
    fun loadResult(folderUri: Uri?, eager: Boolean = false): WorkLoadResult = try {
        val store = if (folderUri == null) loadLocal() else loadFolder(folderUri)
        if (store == null || store.works.isEmpty()) WorkLoadResult.Empty else {
            val selected = selectedWorkId(folderUri)?.takeIf { id -> store.works.any { it.id == id } }
            WorkLoadResult.Loaded(if (selected == null) store else store.copy(activeWorkId = selected))
        }
    } catch (error: Exception) { WorkLoadResult.Failed(WorkLoadFailure.UNAVAILABLE, error.message.orEmpty()) }
    fun saveResult(folderUri: Uri?, works: List<Work>, activeId: String): WorkSaveResult =
        if (save(folderUri, works, activeId)) WorkSaveResult.Saved else WorkSaveResult.Failed
    fun loadWork(folderUri: Uri?, workId: String): Work? =
        (if (folderUri == null) loadLocal() else loadFolder(folderUri))?.works?.find { it.id == workId }
    fun materializeWorks(folderUri: Uri?, works: List<Work>): List<Work> = works
}

/** Persistence boundary for local works, SAF folders, and their asset blobs. */
internal class WorkStoreRepository(
    context: Context,
    private val assetStorage: AssetStorage,
    private val unreadableFolders: MutableSet<String>,
    private val worksFileName: String
) : WorkPersistence {
    private val context = context.applicationContext
    private val resolver get() = context.contentResolver
    private val localSplit = SplitWorkStore(FileWorkDocuments(File(this.context.filesDir, "work-store")))
    private val folderSplits = mutableMapOf<String, SplitWorkStore>()
    private val selection by lazy { context.getSharedPreferences("work-selection", Context.MODE_PRIVATE) }

    override fun selectedWorkId(folderUri: Uri?): String? =
        selection.getString(folderUri?.toString() ?: "local", null)

    // apply updates memory immediately and writes the small selection preference asynchronously.
    override fun rememberSelectedWork(folderUri: Uri?, activeId: String) {
        selection.edit().putString(folderUri?.toString() ?: "local", activeId).apply()
    }

    private fun restoreSelection(store: WorkStore, folderUri: Uri?): WorkStore {
        val id = selectedWorkId(folderUri)?.takeIf { selected -> store.works.any { it.id == selected } }
        return if (id == null) store else store.copy(activeWorkId = id)
    }

    fun pruneUnusedAssets(works: List<Work>, previewAssets: PreviewAssets) {
        val local = AtomicFile(File(context.filesDir, "local-works.json"))
        val localWorks = if (local.baseFile.exists() || File(local.baseFile.path + ".bak").exists()) {
            runCatching { local.openRead().bufferedReader().use { parseWorkStoreJson(it.readText()) } }
                .getOrNull()?.works ?: return
        } else emptyList()
        val recoveryHashes = runCatching {
            localSplit.retainedAssetHashes() + folderSplits.values.flatMap { it.retainedAssetHashes() }
        }.getOrNull() ?: return // Unreadable recovery data must never cause asset deletion.
        val keep = (works + localWorks).flatMap { it.assets.values }.map { it.hash }.toSet() +
            previewAssets.assets.values.map { it.hash } + recoveryHashes
        assetStorage.prune(keep)
    }

    private fun loadLegacyLocal(): WorkStore? {
        val local = AtomicFile(File(context.filesDir, "local-works.json"))
        if (!local.baseFile.exists() && !File(local.baseFile.path + ".bak").exists()) return null
        return local.openRead().bufferedReader().use { parseWorkStoreJson(it.readText()) }
            ?: error("Invalid local works")
    }

    @Synchronized
    override fun loadLocal(): WorkStore? = (loadResult(null, eager = true) as? WorkLoadResult.Loaded)?.store

    @Synchronized
    override fun loadResult(folderUri: Uri?, eager: Boolean): WorkLoadResult {
        val key = folderUri?.toString() ?: "local"
        return try {
            val directory = folderUri?.let { DocumentFile.fromTreeUri(context, it) }
            if (folderUri != null && (directory == null || !directory.exists() || !directory.canRead())) {
                unreadableFolders.add(key)
                return WorkLoadResult.Failed(WorkLoadFailure.UNAVAILABLE, "Cannot access work folder")
            }
            val split = if (directory == null) localSplit else splitFolder(directory)
            val preferredId = selectedWorkId(folderUri)
            val store = if (directory == null) {
                split.loadOrMigrate(::loadLegacyLocal, { legacy ->
                    check(legacy.works.flatMap { it.assets.values }.all(assetStorage::contains))
                    normalizeLegacyRuntime(legacy)
                }, eager, preferredId)
            } else {
                split.loadOrMigrate({ loadWorkDocument(documents(directory), worksFileName) }, { legacy ->
                    restoreFolderAssets(directory, legacy.works)
                    normalizeLegacyRuntime(legacy)
                }, eager, preferredId)
            }
            if (store != null) {
                if (directory == null) check(store.works.flatMap { it.assets.values }.all(assetStorage::contains))
                else restoreFolderAssets(directory, store.works)
            }
            unreadableFolders.remove(key)
            if (store == null || store.works.isEmpty()) WorkLoadResult.Empty else
                WorkLoadResult.Loaded(restoreSelection(store, folderUri), split.recovered, split.deferredWorkIds)
        } catch (error: Exception) {
            unreadableFolders.add(key)
            Log.e("EditKIRO", "Failed to load work store; refusing overwrite", error)
            val reason = if (error is SecurityException || error is java.io.IOException) WorkLoadFailure.UNAVAILABLE else WorkLoadFailure.CORRUPT
            WorkLoadResult.Failed(reason, error.message.orEmpty())
        }
    }

    private fun normalizeLegacyRuntime(store: WorkStore) {
        // Every loaded work is user-authored; never migrate by a former sample ID.
    }

    @Synchronized
    private fun saveLocalResult(works: List<Work>, activeId: String): WorkSaveResult {
        if ("local" in unreadableFolders) return WorkSaveResult.Failed
        return runCatching {
            if (localSplit.loadIfNeeded() == null) loadLegacyLocal()?.let { legacy ->
                normalizeLegacyRuntime(legacy)
                val migrated = localSplit.saveResult(legacy.works, legacy.activeWorkId)
                if (migrated != WorkSaveResult.Saved) return migrated
            }
            check(works.flatMap { it.assets.values }.all(assetStorage::contains))
            localSplit.saveResult(works, activeId)
        }.getOrDefault(WorkSaveResult.Failed)
    }

    private fun saveFolderAssets(directory: DocumentFile, works: List<Work>) {
        val references = works.flatMap { it.assets.values }.distinctBy { it.hash }
        if (references.isEmpty()) return
        val folder = directory.findFile("assets") ?: directory.createDirectory("assets") ?: error("Cannot create assets")
        // Fetch names, sizes and URIs in one provider query, instead of a size query per asset.
        val existingFiles = folderAssetDocuments(folder)
        references.forEach { asset ->
            check(assetStorage.contains(asset))
            val existing = existingFiles[asset.hash]
            if (existing == null || existing.second != asset.size) {
                val uri = existing?.first ?: folder.createFile("application/octet-stream", asset.hash)?.uri ?: error("Cannot save asset")
                resolver.openOutputStream(uri, "wt")?.use { output ->
                    assetStorage.file(asset).inputStream().use { copyBounded(it, output, MAX_ASSET_BYTES) }
                } ?: error("Cannot save asset")
            }
        }
    }

    private fun folderAssetDocuments(folder: DocumentFile): Map<String?, Pair<Uri, Long>> {
        val queried = runCatching {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder.uri, DocumentsContract.getDocumentId(folder.uri))
            val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE)
            resolver.query(children, columns, null, null, null)?.use { cursor ->
                buildMap<String?, Pair<Uri, Long>> {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(1), DocumentsContract.buildDocumentUriUsingTree(folder.uri, cursor.getString(0)) to
                            if (cursor.isNull(2)) -1L else cursor.getLong(2))
                    }
                }
            }
        }.getOrNull()
        return queried ?: folder.listFiles().associate { it.name to (it.uri to it.length()) }
    }

    private fun restoreFolderAssets(directory: DocumentFile, works: List<Work>) {
        val references = works.flatMap { it.assets.values }.distinctBy { it.hash }
        if (references.isEmpty()) return
        val folder = directory.findFile("assets") ?: error("Missing assets")
        val missing = references.filterNot(assetStorage::contains)
        if (missing.isEmpty()) return
        val existingFiles = folder.listFiles().associateBy { it.name }
        missing.forEach { asset ->
            val document = existingFiles[asset.hash] ?: error("Missing asset")
            val loaded = resolver.openInputStream(document.uri)?.use { assetStorage.put(it, asset.mime, asset.hash) }
            check(loaded?.size == asset.size)
        }
    }

    private fun documents(directory: DocumentFile): WorkDocuments = object : WorkDocuments {
        private var children: MutableMap<String, DocumentFile>? = null
        private fun files(): MutableMap<String, DocumentFile> = children ?: directory.listFiles()
            .mapNotNull { file -> file.name?.let { it to file } }.toMap().toMutableMap().also { children = it }
        override fun refresh() { children = null }
        override fun exists(name: String) = name in files()
        override fun read(name: String): String? = files()[name]?.let { file ->
            resolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
                ?: throw java.io.IOException("Cannot read work document")
        }
        override fun write(name: String, content: String): Boolean {
            val file = files()[name] ?: directory.createFile("application/octet-stream", name) ?: return false
            files()[name] = file
            resolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(content) } ?: return false
            return file.name == name
        }
        override fun rename(from: String, to: String): Boolean {
            val file = files()[from] ?: return false
            if (to in files()) return false
            if (!file.renameTo(to) || file.name != to) { refresh(); return false }
            files().remove(from); files()[to] = file
            return true
        }
        override fun delete(name: String): Boolean {
            val file = files()[name] ?: return false
            if (!file.delete()) return false
            files().remove(name)
            return true
        }
    }

    private fun splitFolder(directory: DocumentFile): SplitWorkStore {
        return folderSplits.getOrPut(directory.uri.toString()) {
            // Do not create a directory for read-only/empty-folder checks.
            SplitWorkStore(object : WorkDocuments {
                private var delegate: WorkDocuments? = null
                private fun docs(create: Boolean = false): WorkDocuments? {
                    delegate?.let { return it }
                    val folder = directory.findFile("edit-rin-works")
                        ?: if (create) directory.createDirectory("edit-rin-works") else null
                    if (folder == null) return null
                    check(folder.isDirectory)
                    return documents(folder).also { delegate = it }
                }
                override fun refresh() { delegate = null }
                override fun exists(name: String) = docs()?.exists(name) == true
                override fun read(name: String) = docs()?.read(name)
                override fun write(name: String, content: String) = docs(true)?.write(name, content) == true
                override fun rename(from: String, to: String) = docs()?.rename(from, to) == true
                override fun delete(name: String) = docs()?.delete(name) == true
            })
        }
    }

    @Synchronized
    override fun loadFolder(folderUri: Uri): WorkStore? =
        (loadResult(folderUri, eager = true) as? WorkLoadResult.Loaded)?.store

    @Synchronized
    override fun loadWork(folderUri: Uri?, workId: String): Work? {
        if (folderUri == null) return localSplit.loadWork(workId)?.also { work ->
            check(work.assets.values.all(assetStorage::contains))
        }
        val directory = checkNotNull(DocumentFile.fromTreeUri(context, folderUri))
        return splitFolder(directory).loadWork(workId)?.also { restoreFolderAssets(directory, listOf(it)) }
    }

    @Synchronized
    override fun materializeWorks(folderUri: Uri?, works: List<Work>): List<Work> {
        if (folderUri == null) return localSplit.materializeWorks(works).also { full ->
            check(full.flatMap { it.assets.values }.all(assetStorage::contains))
        }
        val directory = checkNotNull(DocumentFile.fromTreeUri(context, folderUri))
        return splitFolder(directory).materializeWorks(works).also { restoreFolderAssets(directory, it) }
    }

    @Synchronized
    override fun save(folderUri: Uri?, works: List<Work>, activeId: String): Boolean =
        saveResult(folderUri, works, activeId) == WorkSaveResult.Saved

    @Synchronized
    override fun saveResult(folderUri: Uri?, works: List<Work>, activeId: String): WorkSaveResult {
        val result = if (folderUri == null) saveLocalResult(works, activeId) else {
            if (folderUri.toString() in unreadableFolders) return WorkSaveResult.Failed
            try {
                val directory = DocumentFile.fromTreeUri(context, folderUri) ?: return WorkSaveResult.Failed
                val split = splitFolder(directory)
                if (split.loadIfNeeded() == null) {
                    loadWorkDocument(documents(directory), worksFileName)?.let { legacy ->
                        restoreFolderAssets(directory, legacy.works)
                        normalizeLegacyRuntime(legacy)
                        val migrated = split.saveResult(legacy.works, legacy.activeWorkId)
                        if (migrated != WorkSaveResult.Saved) return migrated
                    }
                }
                saveFolderAssets(directory, split.worksWithChangedAssets(works))
                split.saveResult(works, activeId)
            } catch (error: Exception) {
                Log.e("EditKIRO", "Failed to save work store", error)
                WorkSaveResult.Failed
            }
        }
        if (result == WorkSaveResult.Saved) rememberSelectedWork(folderUri, activeId)
        return result
    }
}
