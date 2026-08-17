package com.adproject.candidate

import com.adproject.candidate.feature.profile.AvatarReadResult
import com.adproject.candidate.feature.profile.readAvatarBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class AvatarBytesTest {
    @Test fun readsSmallStreamWithinLimit() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val result = readAvatarBytes(ByteArrayInputStream(bytes), 10)
        assertTrue(result is AvatarReadResult.Ok)
        assertArrayEquals(bytes, (result as AvatarReadResult.Ok).bytes)
    }

    @Test fun readsExactlyAtLimitAsOk() {
        val result = readAvatarBytes(ByteArrayInputStream(ByteArray(16)), 16)
        assertTrue(result is AvatarReadResult.Ok)
        assertEquals(16, (result as AvatarReadResult.Ok).bytes.size)
    }

    @Test fun overLimitReturnsTooLarge() {
        val result = readAvatarBytes(ByteArrayInputStream(ByteArray(17)), 16)
        assertTrue(result is AvatarReadResult.TooLarge)
    }

    @Test fun emptyStreamReturnsOkEmpty() {
        val result = readAvatarBytes(ByteArrayInputStream(ByteArray(0)), 16)
        assertTrue(result is AvatarReadResult.Ok)
        assertEquals(0, (result as AvatarReadResult.Ok).bytes.size)
    }

    @Test fun overLimitDoesNotReadBeyondCap() {
        val counting = CountingInputStream(ByteArrayInputStream(ByteArray(1000)))
        val result = readAvatarBytes(counting, 5)
        assertTrue(result is AvatarReadResult.TooLarge)
        // Cap of 5 means we may read at most 5 + 1 = 6 bytes before stopping.
        assertTrue("read ${counting.bytesRead} bytes, expected <= 6", counting.bytesRead <= 6)
    }

    private class CountingInputStream(private val source: ByteArrayInputStream) : InputStream() {
        var bytesRead = 0
            private set
        override fun read(): Int = source.read().also { if (it >= 0) bytesRead++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            source.read(b, off, len).also { if (it > 0) bytesRead += it }
    }
}
