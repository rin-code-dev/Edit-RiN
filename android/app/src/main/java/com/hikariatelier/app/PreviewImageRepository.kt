package com.hikariatelier.app

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.FileObserver
import android.util.Base64
import android.util.LruCache
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** All preview consumers share decoded images and in-flight reads for this Activity. */
@Suppress("DEPRECATION")
internal class PreviewImageRepository(
    private val cacheDir: File,
    private val assets: AssetManager,
    parent: CoroutineScope,
    private val metrics: PerformanceMeasurements = PerformanceMeasurements(),
    private val decoder: (File) -> Bitmap? = { BitmapFactory.decodeFile(it.path) }
) {
    private data class Cached(val bitmap: Bitmap, val modified: Long, val length: Long)
    private val bitmaps = object : LruCache<String, Cached>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Cached) = value.bitmap.allocationByteCount
    }
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    private val files = java.util.concurrent.ConcurrentHashMap<String, File>()
    private val fileIds = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val lock = Any()
    private val reads = mutableMapOf<String, Deferred<Bitmap?>>()
    private val permits = Semaphore(2)
    private val catalog = BundledThumbnailCatalog(assets)
    private val changesMutable = MutableSharedFlow<String?>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val changes = changesMutable.asSharedFlow()
    private var observer: FileObserver? = null
    private var rootObserver: FileObserver? = null
    @Volatile private var closed = false
    private var epoch = 0L

    init {
        scope.launch(Dispatchers.IO) {
            val directory = File(cacheDir, "work-previews")
            directory.mkdirs()
            synchronized(lock) {
                if (!closed) {
                    watchDirectory(directory)
                    rootObserver = object : FileObserver(cacheDir.path, FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM) {
                        override fun onEvent(event: Int, path: String?) {
                            if (path != "work-previews") return
                            synchronized(lock) {
                                if (!closed) { watchDirectory(directory); invalidate(null) }
                            }
                        }
                    }.also { it.startWatching() }
                }
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun watchDirectory(directory: File) {
        observer?.stopWatching()
        observer = object : FileObserver(directory.path, FileObserver.CLOSE_WRITE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM or FileObserver.DELETE_SELF) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null || path.matches(Regex("[a-f0-9]{64}\\.png"))) invalidate(path)
            }
        }.also { it.startWatching() }
    }
    private fun invalidate(name: String?) {
        synchronized(lock) {
            if (closed) return
            epoch++
            if (name == null) bitmaps.evictAll() else bitmaps.remove(File(cacheDir, "work-previews/$name").path)
        }
        changesMutable.tryEmit(name)
    }
    fun file(id: String): File {
        files[id]?.let { return it }
        val created = workPreviewFile(cacheDir, id)
        val file = files.putIfAbsent(id, created) ?: created
        fileIds[file.name] = id
        return file
    }
    fun idForFile(name: String): String? = fileIds[name]
    fun peek(id: String): Bitmap? {
        val start = System.nanoTime()
        return bitmaps.get(file(id).path)?.bitmap?.also {
            metrics.record(PerformanceOperation.IMAGE_CACHE_HIT, System.nanoTime() - start)
        }
    }

    suspend fun load(id: String, sample: Work? = null, input: PreviewRunInput? = null): Bitmap? {
        val file = file(id)
        val builtin = if (sample?.isSample == true && input != null) withContext(Dispatchers.IO) {
            catalog.assetPath(sample, input)
        } else null
        val deferred = synchronized(lock) {
            val key = "${file.path}:$builtin:$epoch"
            reads[key] ?: scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                permits.withPermit {
                    metrics.measure(PerformanceOperation.IMAGE_READ) {
                        val cacheStart = System.nanoTime()
                        val modified = file.lastModified(); val length = file.length()
                        val cached = bitmaps.get(file.path)?.takeIf { it.modified == modified && it.length == length }?.bitmap
                        if (cached != null) {
                            metrics.record(PerformanceOperation.IMAGE_CACHE_HIT, System.nanoTime() - cacheStart)
                            cached
                        } else runCatching {
                                val decoded = (if (length > 0) decoder(file) else null)
                                    ?: builtin?.let { assets.open(it).use(BitmapFactory::decodeStream) }
                                ensureActive()
                                if (decoded != null && file.lastModified() == modified && file.length() == length)
                                    bitmaps.put(file.path, Cached(decoded, modified, length))
                                decoded
                            }.getOrElse { if (it is CancellationException) throw it else null }
                    }
                }
            }.also { job ->
                reads[key] = job
                job.invokeOnCompletion { synchronized(lock) { if (reads[key] === job) reads.remove(key) } }
            }
        }
        return deferred.await()
    }
    suspend fun store(id: String, bytes: ByteArray, expectedModified: Long? = null, fingerprint: String? = null): Boolean =
        metrics.measure(PerformanceOperation.IMAGE_WRITE) {
            val file = file(id)
            storeWorkPreviewBytes(file, bytes, expectedModified, fingerprint).also { if (it) invalidate(file.name) }
        }
    suspend fun storeEncoded(id: String, encoded: String, fingerprint: String?): Bitmap? {
        val bytes = withContext(Dispatchers.Default) { runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() } ?: return null
        if (!store(id, bytes, fingerprint = fingerprint)) return null
        return load(id)
    }
    fun close() {
        synchronized(lock) {
            closed = true; observer?.stopWatching(); rootObserver?.stopWatching(); bitmaps.evictAll()
        }
        scope.cancel()
    }
}
