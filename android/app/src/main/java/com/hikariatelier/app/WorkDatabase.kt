package com.hikariatelier.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * High-performance SQLite (WAL mode) + FTS4 full-text search index for works metadata & code.
 * Eliminates O(N) full-store scans during filtering, tag lookup, and full-text search.
 */
internal class WorkDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "edit_rin_works_v3.db",
    null,
    1
) {
    private val lastSyncedSignatures = ConcurrentHashMap<String, Int>()

    init {
        setWriteAheadLoggingEnabled(true)
    }

    private fun workSignature(work: Work, documentFile: String): Int {
        var result = work.id.hashCode()
        result = 31 * result + work.title.hashCode()
        result = 31 * result + work.folderName.hashCode()
        result = 31 * result + if (work.isPinned) 1 else 0
        result = 31 * result + work.tags.hashCode()
        result = 31 * result + work.previewAspectRatio.hashCode()
        result = 31 * result + work.p5Version.hashCode()
        result = 31 * result + if (work.p5SoundEnabled) 1 else 0
        result = 31 * result + work.libraries.hashCode()
        result = 31 * result + work.createdAt.hashCode()
        result = 31 * result + work.updatedAt.hashCode()
        result = 31 * result + documentFile.hashCode()
        result = 31 * result + if (work.bodyLoaded) 1 else 0
        if (work.bodyLoaded) {
            result = 31 * result + work.code.hashCode()
            result = 31 * result + work.files.hashCode()
        }
        return result
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS works_meta (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                folder_name TEXT NOT NULL DEFAULT '',
                is_pinned INTEGER NOT NULL DEFAULT 0,
                tags_json TEXT NOT NULL DEFAULT '[]',
                preview_ratio TEXT NOT NULL DEFAULT '1:1',
                p5_version TEXT NOT NULL DEFAULT '2.3.4',
                sound_enabled INTEGER NOT NULL DEFAULT 0,
                libraries_json TEXT NOT NULL DEFAULT '{}',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                document_file TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_works_updated ON works_meta(is_pinned DESC, updated_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_works_folder ON works_meta(folder_name)")

        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS works_fts USING fts4(
                work_id,
                title,
                tags,
                code_body,
                notindexed=work_id
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    fun syncWork(work: Work, documentFile: String = "") {
        val signature = workSignature(work, documentFile)
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            upsertWorkInternal(db, work, documentFile)
            db.setTransactionSuccessful()
            lastSyncedSignatures[work.id] = signature
        } finally {
            db.endTransaction()
        }
    }

    fun syncAllWorks(works: List<Work>, fileMap: Map<String, String> = emptyMap()) {
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        val updatedSignatures = mutableListOf<Pair<String, Int>>()
        val removedIds = mutableListOf<String>()
        try {
            val keepIds = works.map { it.id }.toSet()
            val existingIds = mutableSetOf<String>()
            val existingFiles = mutableMapOf<String, String>()
            db.rawQuery("SELECT id, document_file FROM works_meta", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    existingIds.add(id)
                    existingFiles[id] = if (cursor.isNull(1)) "" else cursor.getString(1)
                }
            }
            for (oldId in existingIds) {
                if (oldId !in keepIds) {
                    db.delete("works_meta", "id = ?", arrayOf(oldId))
                    db.delete("works_fts", "work_id = ?", arrayOf(oldId))
                    removedIds.add(oldId)
                }
            }
            for (work in works) {
                val docFile = fileMap[work.id] ?: existingFiles[work.id].orEmpty()
                val signature = workSignature(work, docFile)
                if (work.id !in existingIds || lastSyncedSignatures[work.id] != signature) {
                    upsertWorkInternal(db, work, docFile)
                    updatedSignatures.add(work.id to signature)
                }
            }
            db.setTransactionSuccessful()
            removedIds.forEach { lastSyncedSignatures.remove(it) }
            updatedSignatures.forEach { (id, sig) -> lastSyncedSignatures[id] = sig }
        } finally {
            db.endTransaction()
        }
    }

    private fun upsertWorkInternal(db: SQLiteDatabase, work: Work, documentFile: String) {
        val values = ContentValues().apply {
            put("id", work.id)
            put("title", work.title)
            put("folder_name", work.folderName)
            put("is_pinned", if (work.isPinned) 1 else 0)
            put("tags_json", JSONArray(work.tags).toString())
            put("preview_ratio", work.previewAspectRatio)
            put("p5_version", work.p5Version)
            put("sound_enabled", if (work.p5SoundEnabled) 1 else 0)
            put("libraries_json", JSONObject(work.libraries).toString())
            put("created_at", work.createdAt)
            put("updated_at", work.updatedAt)
            put("document_file", documentFile)
        }
        db.insertWithOnConflict("works_meta", null, values, SQLiteDatabase.CONFLICT_REPLACE)

        if (work.bodyLoaded) {
            db.delete("works_fts", "work_id = ?", arrayOf(work.id))
            val combinedCode = buildString {
                append(work.code)
                work.files.forEach { (name, content) ->
                    append('\n').append(name).append('\n').append(content)
                }
            }
            val ftsValues = ContentValues().apply {
                put("work_id", work.id)
                put("title", work.title)
                put("tags", work.tags.joinToString(" "))
                put("code_body", combinedCode)
            }
            db.insert("works_fts", null, ftsValues)
        }
    }

    fun searchWorkIds(rawQuery: String): Set<String> {
        val cleaned = rawQuery.trim().replace("\"", "")
        if (cleaned.isEmpty()) return emptySet()
        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }.take(16)
        if (tokens.isEmpty()) return emptySet()

        val matched = linkedSetOf<String>()

        // 1. FTS4 MATCH query (prefix token search, fast)
        runCatching {
            val ftsQuery = tokens.joinToString(" AND ") { "\"$it*\"" }
            readableDatabase.rawQuery(
                "SELECT work_id FROM works_fts WHERE works_fts MATCH ?",
                arrayOf(ftsQuery)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    matched.add(cursor.getString(0))
                }
            }
        }

        // 2. LIKE query on works_fts (handles substring matches like "Canvas" in "createCanvas" and CJK characters)
        runCatching {
            val whereClauses = tokens.joinToString(" AND ") {
                "(title LIKE ? ESCAPE '/' OR tags LIKE ? ESCAPE '/' OR code_body LIKE ? ESCAPE '/')"
            }
            val args = tokens.flatMap { token ->
                val escaped = token.replace("/", "//").replace("%", "/%").replace("_", "/_")
                val pattern = "%$escaped%"
                listOf(pattern, pattern, pattern)
            }.toTypedArray()

            readableDatabase.rawQuery(
                "SELECT work_id FROM works_fts WHERE $whereClauses",
                args
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    matched.add(cursor.getString(0))
                }
            }
        }

        // 3. LIKE query on works_meta (handles works whose code bodies are not loaded yet into FTS)
        runCatching {
            val whereClauses = tokens.joinToString(" AND ") {
                "(title LIKE ? ESCAPE '/' OR folder_name LIKE ? ESCAPE '/' OR tags_json LIKE ? ESCAPE '/')"
            }
            val args = tokens.flatMap { token ->
                val escaped = token.replace("/", "//").replace("%", "/%").replace("_", "/_")
                val pattern = "%$escaped%"
                listOf(pattern, pattern, pattern)
            }.toTypedArray()

            readableDatabase.rawQuery(
                "SELECT id FROM works_meta WHERE $whereClauses",
                args
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    matched.add(cursor.getString(0))
                }
            }
        }

        return matched
    }

    fun searchCodeMatches(rawQuery: String, limit: Int = 60): Map<String, GalleryCodeMatch> {
        val cleaned = rawQuery.trim().replace("\"", "")
        if (cleaned.isEmpty()) return emptyMap()
        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }.take(8)
        if (tokens.isEmpty()) return emptyMap()

        val results = linkedMapOf<String, GalleryCodeMatch>()
        runCatching {
            val whereClauses = tokens.joinToString(" AND ") { "code_body LIKE ? ESCAPE '/'" }
            val args = tokens.map { token ->
                val escaped = token.replace("/", "//").replace("%", "/%").replace("_", "/_")
                "%$escaped%"
            }.toTypedArray()

            readableDatabase.rawQuery(
                "SELECT work_id, code_body FROM works_fts WHERE $whereClauses LIMIT $limit",
                args
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val workId = cursor.getString(0)
                    val codeBody = cursor.getString(1).orEmpty()
                    findFirstCodeMatchInCombinedCode(workId, codeBody, cleaned)?.let { match ->
                        results[workId] = match
                    }
                }
            }
        }
        return results
    }

    companion object {
        @Volatile
        private var instance: WorkDatabase? = null

        fun getInstance(context: Context): WorkDatabase =
            instance ?: synchronized(this) {
                instance ?: WorkDatabase(context.applicationContext).also { instance = it }
            }
    }
}
