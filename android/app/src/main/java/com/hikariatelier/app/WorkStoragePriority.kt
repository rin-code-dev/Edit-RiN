package com.hikariatelier.app

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Register foreground intent before waiting for the store lock. Admitted IO is never aborted. */
internal class WorkStoragePriority {
    private val store = ReentrantLock(true)
    private val pendingHigh = AtomicInteger()
    private val admission = ReentrantLock()
    private val foregroundIdle = admission.newCondition()

    fun <T> high(action: () -> T): T {
        pendingHigh.incrementAndGet()
        try { return store.withLock(action) }
        finally {
            admission.withLock {
                pendingHigh.decrementAndGet()
                foregroundIdle.signalAll()
            }
        }
    }
    fun <T> low(action: () -> T): T {
        while (true) {
            admission.withLock { while (pendingHigh.get() > 0) foregroundIdle.await() }
            store.lock()
            try {
                if (pendingHigh.get() == 0) return action()
            } finally { store.unlock() }
        }
    }
    internal val foregroundPending get() = pendingHigh.get()
}
