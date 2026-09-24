package com.hikariatelier.app

/** Small document operations keep SAF's provider behavior testable without an Android device. */
internal interface WorkDocuments {
    fun exists(name: String): Boolean
    fun read(name: String): String?
    fun write(name: String, content: String): Boolean
    fun rename(from: String, to: String): Boolean
    fun delete(name: String): Boolean
}

internal fun loadWorkDocument(documents: WorkDocuments, name: String): WorkStore? {
    val backup = "$name.backup"
    val pending = "$name.pending"
    if (!documents.exists(name) && !documents.exists(backup) && !documents.exists(pending)) return null
    for (candidate in listOf(name, backup, pending)) {
        val store = runCatching { documents.read(candidate)?.let(::parseWorkStoreJson) }.getOrNull()
        if (store != null && store.works.isNotEmpty()) return store
    }
    error("No readable work document")
}

/** Keep the previous valid document until the replacement can be read back. */
internal fun saveWorkDocument(documents: WorkDocuments, name: String, json: String): Boolean {
    val pending = "$name.pending"
    val backup = "$name.backup"
    if (documents.exists(pending) && !documents.delete(pending)) return false
    try {
        if (!documents.write(pending, json) || documents.read(pending) != json) return false

        if (documents.exists(name)) {
            val current = runCatching { documents.read(name)?.let(::parseWorkStoreJson) }.getOrNull()
            if (current != null && current.works.isNotEmpty()) {
                if (documents.exists(backup) && !documents.delete(backup)) return false
                if (!documents.rename(name, backup)) return false
            } else {
                val previous = runCatching { documents.read(backup)?.let(::parseWorkStoreJson) }.getOrNull()
                if (previous == null || previous.works.isEmpty() || !documents.delete(name)) return false
            }
        }

        if (!documents.rename(pending, name)) {
            if (!documents.exists(name) && documents.exists(backup)) documents.rename(backup, name)
            return false
        }
        if (documents.read(name) != json) {
            documents.delete(name)
            if (documents.exists(backup)) documents.rename(backup, name)
            return false
        }
        return true
    } finally {
        if (documents.exists(pending)) documents.delete(pending)
    }
}
