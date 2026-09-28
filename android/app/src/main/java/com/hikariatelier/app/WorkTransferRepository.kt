package com.hikariatelier.app

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal fun documentDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        } else null
    }
}.getOrNull()

/** Owns document streams, backup archives, snapshots, and remote p5 input. */
internal class WorkTransferRepository(context: Context, private val assetStorage: AssetStorage) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val filesDir = app.filesDir

    suspend fun readAssets(uris: List<Uri>, existing: Map<String, ProjectAsset>): Map<String, ProjectAsset> =
        withContext(Dispatchers.IO) {
            val updated = existing.toMutableMap()
            require(updated.size + uris.size <= 100)
            uris.forEach { uri ->
                val name = uniqueAssetName(documentDisplayName(resolver, uri) ?: "asset", updated.keys)
                val mime = assetMimeType(name, resolver.getType(uri))
                updated[name] = resolver.openInputStream(uri)?.use { assetStorage.put(it, mime) }
                    ?: error("Cannot open asset")
                validateAssetSet(updated)
            }
            updated.toMap()
        }

    suspend fun readJs(uri: Uri): Work = withContext(Dispatchers.IO) {
        val code = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Cannot read JS file")
        val title = documentDisplayName(resolver, uri)?.removeSuffix(".js")?.removeSuffix(".JS")
            ?.ifBlank { null } ?: "Imported sketch"
        Work(id = java.util.UUID.randomUUID().toString(), title = title, code = code)
    }

    suspend fun writeJs(uri: Uri, code: String) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(code) }
            ?: error("Cannot write JS file")
    }

    suspend fun writeBackup(uri: Uri, works: List<Work>, activeId: String, settings: String) =
        withContext(Dispatchers.IO) {
            val output = resolver.openOutputStream(uri, "wt") ?: error("Cannot open backup")
            writeAssetBackup(output, works, activeId, settings, assetStorage,
                WorkSnapshotStore.exportSnapshots(filesDir, works))
        }

    suspend fun readBackup(uri: Uri): AssetBackup = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { readAssetBackup(it, assetStorage) }
            ?: error("Cannot open backup")
    }

    suspend fun commitBackup(backup: AssetBackup, save: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        WorkSnapshotStore.importWithWorks(filesDir, backup.snapshots, save)
    }

    suspend fun readWorkZip(uri: Uri): AssetBackup {
        val backup = readBackup(uri)
        require(backup.store.works.size == 1)
        val original = backup.store.works.single()
        val imported = Work(id = java.util.UUID.randomUUID().toString(), title = original.title,
            code = original.code, files = original.files.toMutableMap(), assets = original.assets.toMap(),
            previewAspectRatio = original.previewAspectRatio, p5Version = original.p5Version,
            p5SoundEnabled = original.p5SoundEnabled, libraries = original.libraries.toMap(),
            parameterValues = original.parameterValues.toMap(), isPinned = original.isPinned,
            tags = original.tags.toList())
        return AssetBackup(WorkStore(listOf(imported), imported.id), null,
            mapOf(imported.id to backup.snapshots[original.id].orEmpty()))
    }

    suspend fun fetchAccount(username: String): List<P5Sketch> = withContext(Dispatchers.IO) {
        fetchP5Sketches(username)
    }

    suspend fun readP5(sketch: P5Sketch): Work = withContext(Dispatchers.IO) {
        val imported = importP5Sketch(sketch, assetStorage)
        Work(id = java.util.UUID.randomUUID().toString(), title = imported.name,
            code = imported.code, files = imported.supportingFiles.toMutableMap(), assets = imported.assets,
            p5Version = imported.p5Version, p5SoundEnabled = imported.p5SoundEnabled)
    }
}

internal fun safeJsFileName(title: String): String {
    val base = title.trim().ifBlank { "sketch" }.replace(Regex("""[\\/:*?"<>|]+"""), "_")
    return if (base.endsWith(".js", ignoreCase = true)) base else "$base.js"
}
