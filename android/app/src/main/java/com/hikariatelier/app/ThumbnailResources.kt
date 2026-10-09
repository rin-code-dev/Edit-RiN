package com.hikariatelier.app

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** Thermal/power notifications wake the queue; memory is sampled only around actual work. */
internal class ThumbnailResources(private val context: Context) {
    private val memory = context.getSystemService(ActivityManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    fun workerCount(load: ThumbnailLoadState = ThumbnailLoadState()): Int {
        val info = ActivityManager.MemoryInfo().also(memory::getMemoryInfo)
        val runtime = Runtime.getRuntime()
        return thumbnailWorkerCount(ThumbnailDeviceState(runtime.availableProcessors(), memory.memoryClass,
            info.availMem / (1024 * 1024), memory.isLowRamDevice, info.lowMemory, power.isPowerSaveMode,
            power.currentThermalStatus,
            (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)), load)
    }
    val changes get() = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { trySend(Unit) }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        val thermal = PowerManager.OnThermalStatusChangedListener { trySend(Unit) }
        power.addThermalStatusListener(context.mainExecutor, thermal)
        trySend(Unit)
        awaitClose {
            context.unregisterReceiver(receiver)
            power.removeThermalStatusListener(thermal)
        }
    }
}
