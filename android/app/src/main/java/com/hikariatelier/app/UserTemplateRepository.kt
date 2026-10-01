package com.hikariatelier.app

import java.io.File
import java.util.UUID

/** A reusable starting point has its own ID and no editing history or gallery metadata. */
internal fun newWorkFromUserTemplate(source: Work, title: String): Work = Work(
    id = UUID.randomUUID().toString(), title = title, code = source.code,
    files = source.files.toMutableMap(), assets = source.assets.toMap(),
    previewAspectRatio = source.previewAspectRatio, p5Version = source.p5Version,
    p5SoundEnabled = source.p5SoundEnabled, libraries = source.libraries.toMap(),
    parameterValues = source.parameterValues.toMap()
)

internal interface UserTemplatePersistence {
    fun load(): List<Work>
    fun save(templates: List<Work>)
    fun captureAssets(template: Work)
    fun prepareAssets(template: Work)
}

/** Call on IO. Independent blobs survive deletion/pruning of the original work's assets. */
internal class UserTemplateRepository(filesDir: File, private val workAssets: AssetStorage) : UserTemplatePersistence {
    private val metadata = SnapshotAtomicFile(File(filesDir, "user-templates/templates.json"))
    private val templateAssets = AssetStorage(File(filesDir, "user-templates/assets"))
    internal val backupAssetStorage: AssetStorage get() = templateAssets

    @Synchronized
    override fun load(): List<Work> {
        val json = metadata.read() ?: return emptyList()
        val works = checkNotNull(parseWorkStoreJson(json)) { "Invalid user templates" }.works
        check(works.flatMap { it.assets.values }.all(templateAssets::contains)) { "Missing template assets" }
        return works
    }

    @Synchronized
    override fun save(templates: List<Work>) {
        saveRetainingAssets(templates)
        pruneAssets(templates)
    }

    /** Backup restoration retains both generations until the works/snapshots commit succeeds. */
    @Synchronized
    internal fun saveRetainingAssets(templates: List<Work>) {
        // Refuse to replace unreadable data with an empty/new list.
        load()
        check(templates.flatMap { it.assets.values }.all(templateAssets::contains))
        metadata.write(serializeWorkStore(templates, ""))
    }

    @Synchronized
    internal fun pruneAssets(templates: List<Work>) {
        // Metadata is durable before pruning; cleanup failure must not report a failed commit.
        runCatching { templateAssets.prune(templates.flatMap { it.assets.values }.map { it.hash }.toSet()) }
    }

    @Synchronized
    override fun captureAssets(template: Work) = copyAssets(template, workAssets, templateAssets)

    @Synchronized
    override fun prepareAssets(template: Work) = copyAssets(template, templateAssets, workAssets)

    private fun copyAssets(template: Work, source: AssetStorage, destination: AssetStorage) {
        template.assets.values.distinctBy { it.hash }.forEach { asset ->
            check(source.contains(asset)) { "Missing source asset" }
            if (!destination.contains(asset)) {
                val copy = source.file(asset).inputStream().use { destination.put(it, asset.mime, asset.hash) }
                check(copy.size == asset.size)
            }
        }
    }
}
