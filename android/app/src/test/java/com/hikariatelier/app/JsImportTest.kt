package com.hikariatelier.app

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class JsImportTest {
    /** Models providers with missing size metadata and no available-byte estimate. */
    private class UnknownSizeStream(private val data: ByteArray) : InputStream() {
        private val input = ByteArrayInputStream(data)
        var closed = false
        var bytesRead = 0
        override fun available() = 0
        override fun read(): Int = input.read().also { if (it >= 0) bytesRead++ }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            input.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
        override fun close() { closed = true; input.close() }
    }

    @Test fun exactLimitWithoutSizeMetadataIsAcceptedAndStreamClosed() {
        val input = UnknownSizeStream(ByteArray(MAX_PROJECT_TEXT_BYTES.toInt()) { 'a'.code.toByte() })
        assertEquals(MAX_PROJECT_TEXT_BYTES.toInt(), readImportedJsSource(input).length)
        assertTrue(input.closed)
    }

    @Test fun oversizedSourceStopsReadingAndStreamIsClosed() {
        val input = UnknownSizeStream(ByteArray(MAX_PROJECT_TEXT_BYTES.toInt() + 128 * 1024))
        assertThrows(JsImportTooLargeException::class.java) { readImportedJsSource(input) }
        assertTrue(input.bytesRead <= MAX_PROJECT_TEXT_BYTES + 32 * 1024)
        assertTrue(input.closed)
    }

    @Test fun utf8AndEmptyFilesRemainReadable() {
        val code = "// 日本語\nconst value = '你好';"
        assertEquals(code, readImportedJsSource(ByteArrayInputStream(code.toByteArray(Charsets.UTF_8))))
        assertEquals("", readImportedJsSource(ByteArrayInputStream(byteArrayOf())))
    }

    @Test fun providerReadFailureIsNotReportedAsASizeError() {
        assertThrows(IOException::class.java) {
            readImportedJsSource(object : InputStream() {
                override fun read(): Int = throw IOException("Provider failed")
            })
        }
    }
}
