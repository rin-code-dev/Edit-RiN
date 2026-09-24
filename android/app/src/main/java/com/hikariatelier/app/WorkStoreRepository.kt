package com.hikariatelier.app

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

/** Persistence boundary for local works, SAF folders, and their asset blobs. */
internal class WorkStoreRepository(
    private val context: Context,
    private val assetStorage: AssetStorage,
    private val unreadableFolders: MutableSet<String>,
    private val previewAssets: () -> PreviewAssets,
    private val worksFileName: String
) {
    private val resolver get() = context.contentResolver

    fun pruneUnusedAssets(works: List<Work>) {
        val local = AtomicFile(File(context.filesDir, "local-works.json"))
        val localWorks = if (local.baseFile.exists() || File(local.baseFile.path + ".bak").exists()) {
            runCatching { local.openRead().bufferedReader().use { parseWorkStoreJson(it.readText()) } }
                .getOrNull()?.works ?: return
        } else emptyList()
        val keep = (works + localWorks).flatMap { it.assets.values }.map { it.hash }.toSet() +
            previewAssets().assets.values.map { it.hash }
        assetStorage.prune(keep)
    }

    fun loadLocal(): WorkStore? = try {
        val local = AtomicFile(File(context.filesDir, "local-works.json"))
        if (!local.baseFile.exists() && !File(local.baseFile.path + ".bak").exists()) null else {
            val store = local.openRead().bufferedReader().use { parseWorkStoreJson(it.readText()) }
                ?: error("Invalid local works")
            check(store.works.flatMap { it.assets.values }.all(assetStorage::contains))
            store
        }
    } catch (_: Exception) { unreadableFolders.add("local"); null }

    @Synchronized
    private fun saveLocal(works: List<Work>, activeId: String): Boolean {
        if ("local" in unreadableFolders) return false
        return runCatching {
            val json = serializeWorkStore(works, activeId)
            val atomic = AtomicFile(File(context.filesDir, "local-works.json"))
            val output = atomic.startWrite()
            try { output.write(json.toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
            catch (error: Exception) { atomic.failWrite(output); throw error }
        }.isSuccess
    }

    private fun saveFolderAssets(directory: DocumentFile, works: List<Work>) {
        val references = works.flatMap { it.assets.values }.distinctBy { it.hash }
        if (references.isEmpty()) return
        val folder = directory.findFile("assets") ?: directory.createDirectory("assets") ?: error("Cannot create assets")
        val existingFiles = folder.listFiles().associateBy { it.name }
        references.forEach { asset ->
            check(assetStorage.contains(asset))
            val existing = existingFiles[asset.hash]
            if (existing == null || existing.length() != asset.size) {
                val document = existing ?: folder.createFile("application/octet-stream", asset.hash) ?: error("Cannot save asset")
                resolver.openOutputStream(document.uri, "wt")?.use { output ->
                    assetStorage.file(asset).inputStream().use { copyBounded(it, output, MAX_ASSET_BYTES) }
                } ?: error("Cannot save asset")
            }
        }
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
        override fun exists(name: String) = directory.findFile(name) != null
        override fun read(name: String): String? = directory.findFile(name)?.let { file ->
            resolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
        }
        override fun write(name: String, content: String): Boolean {
            val file = directory.createFile("application/octet-stream", name) ?: return false
            resolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(content) } ?: return false
            return file.name == name
        }
        override fun rename(from: String, to: String): Boolean =
            directory.findFile(from)?.renameTo(to) == true && directory.findFile(to) != null
        override fun delete(name: String): Boolean = directory.findFile(name)?.delete() == true
    }

    fun loadFolder(folderUri: Uri): WorkStore? = try {
        val directory = DocumentFile.fromTreeUri(context, folderUri) ?: error("Cannot access work folder")
        check(directory.exists() && directory.canRead())
        val store = loadWorkDocument(documents(directory), worksFileName)
        if (store != null) restoreFolderAssets(directory, store.works)
        unreadableFolders.remove(folderUri.toString())
        store
    } catch (error: Exception) {
        unreadableFolders.add(folderUri.toString())
        Log.e("EditKIRO", "Failed to load work store; refusing overwrite", error)
        null
    }

    @Synchronized
    fun save(folderUri: Uri?, works: List<Work>, activeId: String): Boolean {
        if (folderUri == null) return saveLocal(works, activeId)
        if (folderUri.toString() in unreadableFolders) return false
        return try {
            val json = serializeWorkStore(works, activeId)
            val directory = DocumentFile.fromTreeUri(context, folderUri) ?: return false
            saveFolderAssets(directory, works)
            saveWorkDocument(documents(directory), worksFileName, json)
        } catch (error: Exception) {
            Log.e("EditKIRO", "Failed to save work store", error)
            false
        }
    }
}
