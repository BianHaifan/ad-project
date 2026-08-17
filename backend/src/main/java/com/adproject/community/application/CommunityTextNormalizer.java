package com.adproject.community.application;

import com.adproject.common.api.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;

final class CommunityTextNormalizer {
    private CommunityTextNormalizer() {}

    static String normalize(String value, String field, int maxCodePoints) {
        String normalized = value == null ? "" : value.strip();
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints == 0 || codePoints > maxCodePoints) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Request validation failed",
                    Map.of(field, "must contain 1 to " + maxCodePoints + " Unicode code points after stripping"));
        }
        return normalized;
    }
}
