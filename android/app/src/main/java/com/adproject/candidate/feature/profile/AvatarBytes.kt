package com.adproject.candidate.feature.profile

import java.io.InputStream

/** Outcome of reading an avatar stream with a hard byte cap. */
sealed interface AvatarReadResult {
    data class Ok(val bytes: ByteArray) : AvatarReadResult
    object TooLarge : AvatarReadResult
}

/**
 * Reads at most [maxBytes] + 1 bytes from [input]. If the stream yields more than
 * [maxBytes], the read stops early and returns [AvatarReadResult.TooLarge], so an
 * oversized upload is never fully buffered in memory. The `+ 1` byte lets us detect
 * "larger than the limit" without reading the rest of the stream.
 */
fun readAvatarBytes(input: InputStream, maxBytes: Int): AvatarReadResult {
    val buffer = ByteArray(maxBytes + 1)
    var total = 0
    while (total < buffer.size) {
        val read = input.read(buffer, total, buffer.size - total)
        if (read < 0) break
        total += read
    }
    return if (total > maxBytes) AvatarReadResult.TooLarge else AvatarReadResult.Ok(buffer.copyOf(total))
}
