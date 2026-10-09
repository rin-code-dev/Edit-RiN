package com.hikariatelier.app

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewImageRepositoryTest {
    private fun png(color: Int) = ByteArrayOutputStream().use { output ->
        val image = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        image.eraseColor(color); image.compress(Bitmap.CompressFormat.PNG, 100, output); image.recycle()
        output.toByteArray()
    }
    @Test fun consumersShareOneReadAndCancellationDoesNotCancelOtherConsumers() = runBlocking {
        val directory = Files.createTempDirectory("preview-shared").toFile()
        workPreviewFile(directory, "work").also { it.parentFile!!.mkdirs(); it.writeBytes(png(-1)) }
        val started = CountDownLatch(1); val release = CountDownLatch(1); val count = AtomicInteger()
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val repository = PreviewImageRepository(directory, RuntimeEnvironment.getApplication().assets, this,
            PerformanceMeasurements(false)) { count.incrementAndGet(); started.countDown(); check(release.await(5, TimeUnit.SECONDS)); bitmap }
        try {
            val first = async { repository.load("work") }
            yield(); assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
            val second = async { repository.load("work") }; yield()
            first.cancelAndJoin(); release.countDown()
            assertSame(bitmap, second.await()); assertEquals(1, count.get())
            assertSame(bitmap, repository.load("work")); assertEquals(1, count.get())
        } finally { release.countDown(); repository.close(); directory.deleteRecursively() }
    }
    @Test fun writingAnImageInvalidatesDecodedDataAndRejectsAnOlderWriter() = runBlocking {
        val directory = Files.createTempDirectory("preview-update").toFile()
        val repository = PreviewImageRepository(directory, RuntimeEnvironment.getApplication().assets, this, PerformanceMeasurements(false))
        try {
            assertTrue(repository.store("work", png(-65536), fingerprint = "red"))
            val red = repository.load("work")!!; val checkpoint = repository.file("work").lastModified()
            assertEquals(-65536, red.getPixel(0, 0))
            assertTrue(repository.store("work", png(-16711936), fingerprint = "green"))
            assertFalse(repository.store("work", png(-16777216), checkpoint, "late"))
            val green = repository.load("work")!!
            assertEquals(-16711936, green.getPixel(0, 0)); assertNotSame(red, green)
            assertSame(green, repository.peek("work"))
        } finally { repository.close(); directory.deleteRecursively() }
    }
    @Test fun samplesUseTheSameCacheAndUserWorksNeverUseBundledFallbacks() = runBlocking {
        val directory = Files.createTempDirectory("preview-samples").toFile()
        val assets = RuntimeEnvironment.getApplication().assets
        val repository = PreviewImageRepository(directory, assets, this, PerformanceMeasurements(false))
        try {
            val sample = sampleCatalog(defaultWorks(assets), emptyList()).first()
            val input = capturePreviewRun(sample, sample.code, sample.files, sample.assets)
            val image = repository.load(sample.id, sample, input)
            assertNotNull(image); assertSame("size=${image!!.width}x${image.height} bytes=${image.allocationByteCount} file=${repository.file(sample.id).lastModified()}/${repository.file(sample.id).length()}", image, repository.peek(sample.id))
            assertNull(repository.load("user-work"))
        } finally { repository.close(); directory.deleteRecursively() }
    }
    @Test fun oldDecoderCannotReplaceANewerCachedImage() = runBlocking {
        val directory = Files.createTempDirectory("preview-late-read").toFile()
        val started = CountDownLatch(1); val release = CountDownLatch(1); val reads = AtomicInteger()
        val repository = PreviewImageRepository(directory, RuntimeEnvironment.getApplication().assets, this,
            PerformanceMeasurements(false)) { file ->
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.path)
            if (reads.incrementAndGet() == 1) { started.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
            bitmap
        }
        try {
            assertTrue(repository.store("work", png(-65536), fingerprint = "red"))
            val old = async { repository.load("work") }; yield()
            assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
            assertTrue(repository.store("work", png(-16711936), fingerprint = "green"))
            val green = repository.load("work")!!
            release.countDown(); assertEquals(-65536, old.await()!!.getPixel(0, 0))
            assertSame(green, repository.peek("work"))
            assertSame(green, repository.load("work"))
        } finally { release.countDown(); repository.close(); directory.deleteRecursively() }
    }
    @Test fun rapidlyRequestedImagesHaveAtMostTwoConcurrentReads() = runBlocking {
        val directory = Files.createTempDirectory("preview-read-limit").toFile()
        val bytes = png(-1)
        (1..12).forEach { id -> workPreviewFile(directory, "work-$id").also { it.parentFile!!.mkdirs(); it.writeBytes(bytes) } }
        val started = CountDownLatch(2); val release = CountDownLatch(1)
        val active = AtomicInteger(); val maximum = AtomicInteger()
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val repository = PreviewImageRepository(directory, RuntimeEnvironment.getApplication().assets, this,
            PerformanceMeasurements(false)) {
            val concurrent = active.incrementAndGet(); maximum.accumulateAndGet(concurrent, ::maxOf)
            try { started.countDown(); check(release.await(5, TimeUnit.SECONDS)); bitmap }
            finally { active.decrementAndGet() }
        }
        try {
            val requests = (1..12).map { async { repository.load("work-$it") } }
            yield(); assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
            assertEquals(2, maximum.get()); release.countDown()
            requests.awaitAll().forEach { assertSame(bitmap, it) }
            assertEquals(2, maximum.get())
        } finally { release.countDown(); repository.close(); directory.deleteRecursively() }
    }
    @Test fun cachedConsumersAreReportedWithoutDecodingAgain() = runBlocking {
        val directory = Files.createTempDirectory("preview-cache-metric").toFile()
        val metrics = PerformanceMeasurements(true)
        val repository = PreviewImageRepository(directory, RuntimeEnvironment.getApplication().assets, this, metrics)
        try {
            assertTrue(repository.store("work", png(-1)))
            val first = repository.load("work")!!
            assertSame(first, repository.peek("work"))
            assertTrue(metrics.operation(PerformanceOperation.IMAGE_CACHE_HIT).count >= 1)
        } finally { repository.close(); directory.deleteRecursively() }
    }

}
