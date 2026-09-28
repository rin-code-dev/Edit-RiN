package com.hikariatelier.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

internal class WorkFolderRepository(context: Context, private val preferences: SharedPreferences) {
    private val app = context.applicationContext
    fun validUri(): Uri? {
        val value = preferences.getString("works_folder_uri", null) ?: return null
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val permitted = app.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        val accessible = permitted && runCatching {
            DocumentFile.fromTreeUri(app, uri)?.let { it.exists() && it.canRead() && it.canWrite() } == true
        }.getOrDefault(false)
        if (!accessible) { preferences.edit().remove("works_folder_uri").apply(); return null }
        return uri
    }
    fun takePermission(uri: Uri) {
        app.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
    fun select(uri: Uri) { preferences.edit().putString("works_folder_uri", uri.toString()).apply() }
    fun name(uri: Uri): String? = runCatching { DocumentFile.fromTreeUri(app, uri)?.name }.getOrNull()
}
