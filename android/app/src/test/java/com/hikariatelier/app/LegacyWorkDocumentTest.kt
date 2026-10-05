package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class LegacyWorkDocumentTest {
    private class FakeDocuments : WorkDocuments {
        val files = mutableMapOf<String, String>()
        override fun exists(name: String) = name in files
        override fun read(name: String) = files[name]
        override fun write(name: String, content: String): Boolean {
            files[name] = content
            return true
        }
        override fun rename(from: String, to: String): Boolean {
            files[to] = files.remove(from) ?: return false
            return true
        }
        override fun delete(name: String) = files.remove(name) != null
    }

    private val name = "hikari_atelier_works.json"
    private fun json(code: String) = serializeWorkStore(listOf(Work("a", "A", code)), "a")

    @Test fun damagedPrimaryLoadsValidBackup() {
        val documents = FakeDocuments().apply {
            files[name] = "{broken"
            files["$name.backup"] = json("old")
        }
        assertEquals("old", loadWorkDocument(documents, name)?.works?.single()?.code)
    }

    @Test fun initialSaveCanRecoverVerifiedPendingDocument() {
        val documents = FakeDocuments().apply { files["$name.pending"] = json("first") }
        assertEquals("first", loadWorkDocument(documents, name)?.works?.single()?.code)
    }
}
