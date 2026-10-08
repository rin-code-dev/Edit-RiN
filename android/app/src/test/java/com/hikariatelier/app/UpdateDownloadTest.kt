package com.hikariatelier.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpdateDownloadTest {
    private val payload = ByteArray(200_000) { (it % 251).toByte() }
    private fun asset(size: Long = payload.size.toLong(), digest: String? = null) = ReleaseAsset("", size, digest)
    private fun copy(asset: ReleaseAsset) = runBlocking {
        val output = ByteArrayOutputStream()
        copyUpdatePayload(ByteArrayInputStream(payload), output, asset) {}
        output.toByteArray()
    }

    @Test fun copiesBoundedPayloadAndReportsCompletion() = runBlocking {
        val values = mutableListOf<Float>()
        val output = ByteArrayOutputStream()
        val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        copyUpdatePayload(ByteArrayInputStream(payload), output, asset(digest = hash)) { values.add(it) }
        assertArrayEquals(payload, output.toByteArray())
        assertEquals(1f, values.last())
        assertTrue(values.zipWithNext().all { (a, b) -> b > a })
    }
    @Test fun rejectsTruncatedResponse() { assertThrows(IllegalStateException::class.java) { copy(asset(payload.size + 1L)) } }
    @Test fun rejectsOversizedResponse() { assertThrows(IllegalStateException::class.java) { copy(asset(payload.size - 1L)) } }
    @Test fun rejectsChecksumMismatch() { assertThrows(IllegalStateException::class.java) { copy(asset(digest = "0".repeat(64))) } }
    @Test fun rejectsUnboundedSizeBeforeCopying() { assertThrows(IllegalArgumentException::class.java) { copy(asset(MAX_UPDATE_BYTES + 1)) } }
    @Test fun cancellationDoesNotFinishPayload() {
        val output = ByteArrayOutputStream()
        assertThrows(CancellationException::class.java) {
            runBlocking { copyUpdatePayload(ByteArrayInputStream(payload), output, asset()) { throw CancellationException() } }
        }
        assertTrue(output.size() < payload.size)
    }
}
