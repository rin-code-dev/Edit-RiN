package com.hikariatelier.app

/** Small document operations keep SAF's provider behavior testable without an Android device. */
internal interface WorkDocuments {
    fun refresh() {}
    fun exists(name: String): Boolean
    fun read(name: String): String?
    fun write(name: String, content: String): Boolean
    fun rename(from: String, to: String): Boolean
    fun delete(name: String): Boolean
}

/** Read-only migration from the old single-file store. New saves use SplitWorkStore. */
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
