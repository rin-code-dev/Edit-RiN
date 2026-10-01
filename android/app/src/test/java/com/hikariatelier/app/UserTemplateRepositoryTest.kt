package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class UserTemplateRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun templateSurvivesSourceAssetPruningAndRestoresAssetsIntoNewWorks() {
        val root = temporary.newFolder()
        val workAssets = AssetStorage(File(root, "project-assets"))
        val asset = workAssets.put(ByteArrayInputStream("image bytes".toByteArray()), "image/png")
        val source = Work("source", "Source", "loadImage('image.png')", assets = mapOf("image.png" to asset))
        val template = newWorkFromUserTemplate(source, "My image template")
        val repository = UserTemplateRepository(root, workAssets)
        repository.captureAssets(template)
        repository.save(listOf(template))
        workAssets.prune(emptySet())
        assertFalse(workAssets.contains(asset))
        val reopened = UserTemplateRepository(root, workAssets)
        val saved = reopened.load().single()
        assertEquals(template.id, saved.id)
        assertEquals(source.assets.toMap(), saved.assets.toMap())
        reopened.prepareAssets(saved)
        assertTrue(workAssets.contains(asset))
        assertEquals("image bytes", workAssets.file(asset).readText())
        // Removing a template must never remove assets of already-created works.
        reopened.save(emptyList())
        assertTrue(reopened.load().isEmpty())
        assertTrue(workAssets.contains(asset))
    }

    @Test fun newWorksPreserveSettingsWithoutSharingMutableStateOrHistory() {
        val source = Work("source", "Original", "main", files = mutableMapOf("helper.js" to "helper"),
            revisions = mutableListOf(WorkRevision("old", 1)), previewAspectRatio = "9:16",
            p5Version = P5_VERSION_LEGACY, p5SoundEnabled = true,
            libraries = mapOf("matter-js" to "0.20.0"), parameterValues = mapOf("speed" to "2"),
            createdAt = 1, updatedAt = 2, isPinned = true, tags = listOf("source tag"))
        val template = newWorkFromUserTemplate(source, "Template")
        val first = newWorkFromUserTemplate(template, "First")
        val second = newWorkFromUserTemplate(template, "Second")
        assertEquals(4, setOf(source.id, template.id, first.id, second.id).size)
        assertEquals("First", first.title)
        assertEquals("main", first.code)
        assertEquals("9:16", first.previewAspectRatio)
        assertEquals(P5_VERSION_LEGACY, first.p5Version)
        assertTrue(first.p5SoundEnabled)
        assertEquals(source.libraries, first.libraries)
        assertEquals(source.parameterValues.toMap(), first.parameterValues.toMap())
        assertTrue(first.revisions.isEmpty())
        assertTrue(first.tags.isEmpty())
        assertFalse(first.isPinned)
        assertTrue(first.createdAt > source.createdAt)
        first.files["helper.js"] = "changed"
        first.parameterValues["speed"] = "9"
        assertEquals("helper", template.files["helper.js"])
        assertEquals("helper", second.files["helper.js"])
        assertEquals("2", source.parameterValues["speed"])
        assertEquals("2", template.parameterValues["speed"])
    }

    @Test fun unreadableMetadataIsKeptAndCannotBeOverwritten() {
        val root = temporary.newFolder()
        val file = File(root, "user-templates/templates.json").apply { parentFile!!.mkdirs(); writeText("corrupt") }
        val repo = UserTemplateRepository(root, AssetStorage(File(root, "project-assets")))
        assertTrue(runCatching { repo.load() }.isFailure)
        assertTrue(runCatching { repo.save(emptyList()) }.isFailure)
        assertEquals("corrupt", file.readText())
    }

    @Test fun interruptedMetadataWriteRecoversPreviousTemplates() {
        val root = temporary.newFolder()
        val file = File(root, "user-templates/templates.json").apply { parentFile!!.mkdirs(); writeText("partial") }
        File(file.path + ".bak").writeText(serializeWorkStore(listOf(Work("saved", "Saved", "code")), ""))
        val repo = UserTemplateRepository(root, AssetStorage(File(root, "project-assets")))
        assertEquals("saved", repo.load().single().id)
        assertFalse(File(file.path + ".bak").exists())
    }

    @Test fun missingAssetsCannotReplacePreviouslySavedTemplates() {
        val root = temporary.newFolder()
        val repo = UserTemplateRepository(root, AssetStorage(File(root, "project-assets")))
        repo.save(listOf(Work("saved", "Saved", "code")))
        val invalid = Work("missing", "Missing", "", assets = mapOf("x.png" to
            ProjectAsset("0".repeat(64), 5, "image/png")))
        assertTrue(runCatching { repo.captureAssets(invalid) }.isFailure)
        assertTrue(runCatching { repo.save(listOf(invalid)) }.isFailure)
        assertEquals("saved", repo.load().single().id)
    }
    @Test fun retainedTemplateAssetsAllowBackupRollbackBeforeFinalCleanup() {
        val root = temporary.newFolder()
        val workAssets = AssetStorage(File(root, "project-assets"))
        val oldAsset = workAssets.put(ByteArrayInputStream(byteArrayOf(1)), "image/png")
        val newAsset = workAssets.put(ByteArrayInputStream(byteArrayOf(2)), "image/png")
        val old = Work("old", "Old", "old", assets = mapOf("old.png" to oldAsset))
        val incoming = Work("new", "New", "new", assets = mapOf("new.png" to newAsset))
        val repo = UserTemplateRepository(root, workAssets)
        repo.captureAssets(old); repo.save(listOf(old))
        repo.captureAssets(incoming); repo.saveRetainingAssets(listOf(incoming))
        assertTrue(repo.backupAssetStorage.contains(oldAsset))
        repo.saveRetainingAssets(listOf(old))
        assertEquals("old", repo.load().single().id)
        assertTrue(repo.backupAssetStorage.contains(oldAsset))
        repo.saveRetainingAssets(listOf(incoming)); repo.pruneAssets(listOf(incoming))
        assertFalse(repo.backupAssetStorage.contains(oldAsset))
        assertTrue(repo.backupAssetStorage.contains(newAsset))
    }

}
