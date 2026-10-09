package com.hikariatelier.app

import android.util.Log
import android.view.Window
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState

internal enum class PerformanceOperation {
    INITIAL_LOAD,
    INTERACTIVE_STARTUP,
    WORK_SWITCH,
    PREVIEW_PREPARE,
    PREVIEW_READY,
    THUMBNAIL,
    THUMBNAIL_INTERRUPTED,
    THUMBNAIL_FAILED,
    IMAGE_READ,
    IMAGE_CACHE_HIT,
    IMAGE_WRITE
}

internal data class TimingSummary(val count: Long = 0, val totalNanos: Long = 0, val maxNanos: Long = 0) {
    fun plus(nanos: Long) = TimingSummary(count + 1, totalNanos + nanos.coerceAtLeast(0), maxOf(maxNanos, nanos))
    fun describe() = "count=$count avgMs=${if (count == 0L) 0 else totalNanos / count / 1_000_000} maxMs=${maxNanos / 1_000_000}"
}
internal data class FrameSummary(val timing: TimingSummary = TimingSummary(), val janky: Long = 0)

/** Bounded aggregates only: no sketch source, work IDs, file paths, or per-frame logs. */
internal class PerformanceMeasurements(
    val enabled: Boolean = BuildConfig.DEBUG,
    val trackLoadState: Boolean = true
) {
    private val operations = mutableMapOf<PerformanceOperation, TimingSummary>()
    private val frames = mutableMapOf<Boolean, FrameSummary>()
    private val recentJanks = BooleanArray(24)
    private var recentJankIndex = 0
    private var consecutiveCleanFrames = 0
    private val _loadFlow = kotlinx.coroutines.flow.MutableStateFlow(ThumbnailLoadState())
    val loadState: ThumbnailLoadState get() = _loadFlow.value
    val loadChanges: kotlinx.coroutines.flow.Flow<ThumbnailLoadState> get() = _loadFlow
    var refreshRateHz: Float = 60f

    @Synchronized fun record(operation: PerformanceOperation, nanos: Long) {
        if (enabled) operations[operation] = (operations[operation] ?: TimingSummary()).plus(nanos)
    }

    @Synchronized fun frame(nanos: Long, janky: Boolean, scrolling: Boolean) {
        if (enabled) {
            val old = frames[scrolling] ?: FrameSummary()
            frames[scrolling] = FrameSummary(old.timing.plus(nanos), old.janky + if (janky) 1 else 0)
        }
        if (trackLoadState) {
            recentJanks[recentJankIndex] = janky
            recentJankIndex = (recentJankIndex + 1) % recentJanks.size
            if (janky) {
                consecutiveCleanFrames = 0
            } else {
                consecutiveCleanFrames++
            }
            val windowJankCount = recentJanks.count { it }
            // Thresholds: >= 6/24 janky frames (~25%) flags jank, >= 12/24 (~50%) flags heavy load.
            // Recovery requires at least 30 consecutive non-jank frames.
            val current = _loadFlow.value
            val next = if (current.jankActive || current.heavyLoad) {
                if (consecutiveCleanFrames >= 30) {
                    ThumbnailLoadState(jankActive = false, heavyLoad = false)
                } else {
                    ThumbnailLoadState(
                        jankActive = true,
                        heavyLoad = current.heavyLoad || windowJankCount >= 12
                    )
                }
            } else {
                ThumbnailLoadState(
                    jankActive = windowJankCount >= 6,
                    heavyLoad = windowJankCount >= 12
                )
            }
            if (next != current) {
                _loadFlow.value = next
            }
        }
    }

    @Synchronized fun operation(operation: PerformanceOperation) = operations[operation] ?: TimingSummary()
    @Synchronized fun frameSummary(scrolling: Boolean) = frames[scrolling] ?: FrameSummary()
    @Synchronized fun report(): String = if (!enabled) "" else buildString {
        append("refreshRate=${refreshRateHz.toInt()}Hz\n")
        frames.forEach { (scrolling, value) ->
            append("frames(scrolling=$scrolling) ${value.timing.describe()} janky=${value.janky}\n")
        }
        operations.forEach { (name, value) -> append("$name ${value.describe()}\n") }
    }.trim()

    suspend fun <T> measure(operation: PerformanceOperation, action: suspend () -> T): T {
        val start = System.nanoTime()
        try {
            return action()
        } finally {
            record(operation, System.nanoTime() - start)
        }
    }

    suspend fun <T> measureDifferentiated(
        successOp: PerformanceOperation,
        interruptedOp: PerformanceOperation,
        failedOp: PerformanceOperation,
        isSuccess: (T) -> Boolean = { true },
        action: suspend () -> T
    ): T {
        val start = System.nanoTime()
        try {
            val result = action()
            record(if (isSuccess(result)) successOp else failedOp, System.nanoTime() - start)
            return result
        } catch (interrupted: ThumbnailInterruptedException) {
            record(interruptedOp, System.nanoTime() - start)
            throw interrupted
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            record(interruptedOp, System.nanoTime() - start)
            throw cancelled
        } catch (error: Exception) {
            record(failedOp, System.nanoTime() - start)
            throw error
        }
    }
}

/** Activity-owned window instrumentation; callbacks copy primitives and return immediately. */
internal class PerformanceMonitor(private val window: Window) {
    val measurements = PerformanceMeasurements()
    private var stats: JankStats? = null

    fun resume() {
        if (!measurements.enabled && !measurements.trackLoadState) return
        measurements.refreshRateHz = window.context.display.refreshRate
        if (stats == null) stats = runCatching {
            JankStats.createAndTrack(window) { frame ->
                val scrolling = frame.states.any { it.key == "Gallery" && it.value == "Scrolling" }
                measurements.frame(frame.frameDurationUiNanos, frame.isJank, scrolling)
            }
        }.getOrNull()
        stats?.isTrackingEnabled = true
    }

    fun galleryScrolling(scrolling: Boolean) {
        if (!measurements.enabled && !measurements.trackLoadState) return
        PerformanceMetricsState.getHolderForHierarchy(window.decorView).state?.let {
            if (scrolling) it.putState("Gallery", "Scrolling") else it.removeState("Gallery")
        }
    }

    fun pause() {
        stats?.isTrackingEnabled = false
        if (measurements.enabled) measurements.report().takeIf { it.isNotEmpty() }?.let {
            Log.i("EditRinPerformance", it)
        }
    }

    fun close() { pause(); stats = null }
}
