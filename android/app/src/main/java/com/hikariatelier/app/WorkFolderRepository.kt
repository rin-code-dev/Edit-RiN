package com.hikariatelier.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

import org.json.JSONArray

internal class WorkFolderRepository(context: Context, private val preferences: SharedPreferences) {
    private val app = context.applicationContext
    fun validUri(): Uri? {
        val value = preferences.getString("works_folder_uri", null) ?: return null
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        // Keep the chosen location through temporary outages and permission loss. The typed
        // load result offers reconnect/retry, instead of silently showing unrelated local works.
        return uri
    }
    fun takePermission(uri: Uri) {
        app.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
    fun select(uri: Uri) { preferences.edit().putString("works_folder_uri", uri.toString()).apply() }
    fun galleryFolders(uri: Uri?): Set<String> = preferences.getStringSet("gallery_folders:${uri ?: "local"}", emptySet()).orEmpty().toSet()
    fun saveGalleryFolders(uri: Uri?, names: Set<String>) {
        check(preferences.edit().putStringSet("gallery_folders:${uri ?: "local"}", names).commit())
    }
    fun galleryTabsOrder(uri: Uri?): List<String> {
        val raw = preferences.getString("gallery_tabs_order:${uri ?: "local"}", null)
        if (raw != null) {
            val json = runCatching { JSONArray(raw) }.getOrNull()
            if (json != null) {
                return List(json.length()) { json.optString(it) }
            }
        }
        val legacy = galleryFolders(uri).toList().sorted()
        return listOf(SAMPLE_FOLDER) + (if (legacy.isNotEmpty()) listOf("") + legacy else emptyList())
    }
    fun saveGalleryTabsOrder(uri: Uri?, tabs: List<String>) {
        val json = JSONArray(tabs).toString()
        val folders = tabs.filter { it != SAMPLE_FOLDER && it.isNotEmpty() }.toSet()
        check(preferences.edit()
            .putString("gallery_tabs_order:${uri ?: "local"}", json)
            .putStringSet("gallery_folders:${uri ?: "local"}", folders)
            .commit())
    }
    fun name(uri: Uri): String? = runCatching { DocumentFile.fromTreeUri(app, uri)?.name }.getOrNull()
}
