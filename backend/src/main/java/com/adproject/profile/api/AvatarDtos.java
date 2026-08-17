package com.adproject.profile.api;

import java.time.Instant;

public final class AvatarDtos {
    private AvatarDtos() {}

    /** Safe avatar metadata returned by upload/delete. Contains no binary content, email, tokens, or paths. */
    public record AvatarMetadata(String userId, String avatarUrl, String contentType, long sizeBytes,
                                 Instant updatedAt) {}

    public record AvatarResponse(AvatarMetadata data) {}
}
