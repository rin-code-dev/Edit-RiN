package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class ThumbnailQueueTest {
    @Test fun priorityChangesDoNotDuplicateRunningWork() {
        val queue = ThumbnailQueue<Int>()
        queue.replace(linkedMapOf("old" to 1, "visible" to 1, "next" to 1))
        assertEquals("visible", queue.claim(listOf("visible"), "old")!!.first)
        assertEquals("next", queue.claim(listOf("visible", "next"), "old")!!.first)
        assertEquals("old", queue.claim(listOf("visible", "next"), "old")!!.first)
        assertNull(queue.claim(emptyList(), "visible"))
    }
    @Test fun anEditDuringCaptureIsRetriedWithTheNewContent() {
        val queue = ThumbnailQueue<Int>()
        queue.replace(mapOf("work" to 1)); val request = queue.claim(emptyList(), "work")!!
        queue.replace(mapOf("work" to 2))
        assertFalse(queue.matches(request.first, request.second))
        queue.finish(request.first, request.second, true)
        assertEquals("work" to 2, queue.claim(emptyList(), "work"))
    }
    @Test fun deletedAndCompletedWorkDoesNotReappearOnUnrelatedEvents() {
        val queue = ThumbnailQueue<Int>()
        queue.replace(mapOf("work" to 1)); queue.claim(emptyList(), "work")
        queue.finish("work", 1, true); queue.replace(mapOf("work" to 1))
        assertFalse(queue.hasPending)
        queue.invalidate("work"); queue.claim(emptyList(), "work")
        queue.replace(emptyMap()); queue.finish("work", 1, false)
        assertFalse(queue.hasPending)
    }
    @Test fun cacheEvictionAndForegroundInterruptionRemainRecoverable() {
        val queue = ThumbnailQueue<Int>()
        queue.replace(mapOf("work" to 1)); queue.claim(emptyList(), "work")
        queue.finish("work", 1, true); queue.invalidate("work")
        val retry = queue.claim(emptyList(), "work")!!
        queue.finish(retry.first, retry.second, false)
        assertEquals(retry, queue.claim(emptyList(), "work"))
    }
    @Test fun unsavedWorkCanWaitWithoutBlockingOtherVisibleWork() {
        val queue = ThumbnailQueue<Int>()
        queue.replace(linkedMapOf("active" to 1, "visible" to 1))
        assertEquals("visible", queue.claim(listOf("active", "visible"), "active", setOf("active"))!!.first)
        assertNull(queue.claim(emptyList(), "active", setOf("active")))
        assertEquals("active", queue.claim(emptyList(), "active")!!.first)
    }
}
