package com.adproject.candidate

import com.adproject.candidate.feature.profile.resolveAvatarUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarMediaUrlTest {
    @Test fun resolvesRelativePathWithRevision() {
        assertEquals(
            "https://api.example.com/api/v1/avatars/u?v=3",
            resolveAvatarUrl("/api/v1/avatars/u", 3, "https://api.example.com/api/v1/"),
        )
    }

    @Test fun rejectsBlankAbsoluteAndUnsafeSchemes() {
        val base = "https://api.example.com/api/v1/"
        assertNull(resolveAvatarUrl(null, 0, base))
        assertNull(resolveAvatarUrl("", 0, base))
        assertNull(resolveAvatarUrl("   ", 0, base))
        assertNull(resolveAvatarUrl("https://evil.example.com/x.png", 0, base))
        assertNull(resolveAvatarUrl("file:///etc/passwd", 0, base))
        assertNull(resolveAvatarUrl("content://media/x", 0, base))
        assertNull(resolveAvatarUrl("javascript:alert(1)", 0, base))
        assertNull(resolveAvatarUrl("data:image/png;base64,xx", 0, base))
        assertNull(resolveAvatarUrl("//evil.example.com/x.png", 0, base))
        assertNull(resolveAvatarUrl("relative/no/slash", 0, base))
    }

    @Test fun trimsTrailingSlashOnBase() {
        assertEquals(
            "https://api.example.com/api/v1/avatars/u?v=0",
            resolveAvatarUrl("/api/v1/avatars/u", 0, "https://api.example.com/api/v1"),
        )
    }
}
