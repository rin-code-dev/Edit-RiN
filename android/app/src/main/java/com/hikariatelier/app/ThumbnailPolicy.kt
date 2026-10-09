package com.hikariatelier.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Keep a foreground editor's CPU/GPU and memory budget ahead of disposable previews. */
internal data class ThumbnailDeviceState(
    val cores: Int, val heapMb: Int, val availableMb: Long,
    val lowRam: Boolean, val lowMemory: Boolean,
    val powerSave: Boolean = false, val thermalStatus: Int = 0, val heapAvailableMb: Long = Long.MAX_VALUE
)

internal data class ThumbnailLoadState(
    val jankActive: Boolean = false,
    val heavyLoad: Boolean = false
)

internal fun thumbnailWorkerCount(
    device: ThumbnailDeviceState,
    load: ThumbnailLoadState = ThumbnailLoadState()
): Int {
    val base = when {
        device.lowMemory || device.availableMb < 96 || device.heapAvailableMb < 24 || device.thermalStatus >= 3 -> 0
        device.lowRam || device.powerSave || device.thermalStatus >= 1 || device.heapAvailableMb < 64 -> 1
        device.cores >= 4 && device.heapMb >= 192 && device.availableMb >= 384 -> 2
        else -> 1
    }
    return when {
        load.heavyLoad -> 0
        load.jankActive -> (base - 1).coerceAtLeast(0)
        else -> base
    }
}

/** Re-evaluate priority between jobs; scrolling never cancels a capture already in progress. */
internal fun nextThumbnailId(remaining: Set<String>, priority: List<String>, activeId: String): String? =
    priority.firstOrNull { it in remaining } ?: activeId.takeIf { it in remaining } ?: remaining.firstOrNull()

/** Distinguish a resource/foreground interruption from a sketch that failed to draw. */
internal class ThumbnailInterruptedException : CancellationException("Thumbnail yielded to foreground work")

internal suspend fun <T> captureThumbnailWhileAllowed(shouldStop: Flow<Boolean>, capture: suspend () -> T): T = coroutineScope {
    val job = async { capture() }
    val monitor = launch { shouldStop.first { it }; job.cancel(ThumbnailInterruptedException()) }
    try { job.await() } finally { monitor.cancel() }
}
