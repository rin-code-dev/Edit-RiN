package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class WorkDocumentTransactionTest {
    private class FakeDocuments : WorkDocuments {
        val files = mutableMapOf<String, String>()
        var truncatePending = false
        var rejectPromotion = false
        override fun exists(name: String) = name in files
        override fun read(name: String) = files[name]
        override fun write(name: String, content: String): Boolean {
            files[name] = if (truncatePending) content.dropLast(1) else content
            return true
        }
        override fun rename(from: String, to: String): Boolean {
            if (rejectPromotion && from.endsWith(".pending")) return false
            files[to] = files.remove(from) ?: return false
            return true
        }
        override fun delete(name: String) = files.remove(name) != null
    }

    private val name = "hikari_atelier_works.json"
    private fun json(code: String) = serializeWorkStore(listOf(Work("a", "A", code)), "a")

    @Test fun successfulSaveKeepsPreviousReadableCopy() {
        val documents = FakeDocuments().apply { files[name] = json("old") }
        assertTrue(saveWorkDocument(documents, name, json("new")))
        assertEquals("new", loadWorkDocument(documents, name)?.works?.single()?.code)
        assertEquals("old", parseWorkStoreJson(documents.files["$name.backup"]!!)!!.works.single().code)
    }

    @Test fun incompletePendingWriteLeavesOriginalIntact() {
        val documents = FakeDocuments().apply { files[name] = json("old"); truncatePending = true }
        assertFalse(saveWorkDocument(documents, name, json("new")))
        assertEquals("old", loadWorkDocument(documents, name)?.works?.single()?.code)
        assertFalse(documents.exists("$name.pending"))
    }

    @Test fun failedPromotionRestoresReadableOriginal() {
        val documents = FakeDocuments().apply { files[name] = json("old"); rejectPromotion = true }
        assertFalse(saveWorkDocument(documents, name, json("new")))
        assertEquals("old", loadWorkDocument(documents, name)?.works?.single()?.code)
    }

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
