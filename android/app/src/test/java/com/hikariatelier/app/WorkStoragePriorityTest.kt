package com.hikariatelier.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class WorkStoragePriorityTest {
    @Test fun foregroundOvertakesWaitingBackgroundWithoutAbortingAdmittedIo() {
        val priority = WorkStoragePriority()
        val pool = Executors.newFixedThreadPool(3)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val order = java.util.Collections.synchronizedList(mutableListOf<String>())
        try {
            val first = pool.submit { priority.low { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); order.add("admitted") } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val background = pool.submit { priority.low { order.add("background") } }
            val foreground = pool.submit { priority.high { order.add("foreground") } }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (priority.foregroundPending == 0 && System.nanoTime() < deadline) Thread.yield()
            assertEquals(1, priority.foregroundPending)
            release.countDown()
            first.get(5, TimeUnit.SECONDS); foreground.get(5, TimeUnit.SECONDS); background.get(5, TimeUnit.SECONDS)
            assertEquals(listOf("admitted", "foreground", "background"), order)
        } finally { release.countDown(); pool.shutdownNow() }
    }
    @Test fun foregroundFailureReleasesBackgroundAndNestedAccessIsSafe() {
        val priority = WorkStoragePriority()
        runCatching { priority.high { priority.high<Unit> { error("failed") } } }
        assertEquals(0, priority.foregroundPending)
        assertEquals("read", priority.low { "read" })
    }
}
