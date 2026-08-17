package com.adproject.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adproject.common.api.ApiException;
import org.junit.jupiter.api.Test;

class CommunityTextNormalizerTest {
    private static final String EMOJI = "\uD83D\uDE00";
    private static final String EM_SPACE = "\u2003";

    @Test
    void acceptsExactlyTwoThousandUnicodeCodePointsWithoutUsingUtf16Length() {
        String value = EMOJI.repeat(2000);
        assertThat(value.length()).isEqualTo(4000);
        assertThat(CommunityTextNormalizer.normalize(value, "body", 2000)).isEqualTo(value);
    }

    @Test
    void rejectsTwoThousandAndOneEmojiAndBmpCodePoints() {
        assertInvalid(EMOJI.repeat(2001), 2000);
        assertInvalid("x".repeat(2001), 2000);
        assertThat(CommunityTextNormalizer.normalize("x".repeat(2000), "body", 2000)).hasSize(2000);
    }

    @Test
    void stripsAsciiAndUnicodeBoundaryWhitespaceButPreservesInternalWhitespace() {
        String internal = "left" + EM_SPACE + "middle  right";
        assertThat(CommunityTextNormalizer.normalize("  " + EM_SPACE + internal + EM_SPACE + "  ", "body", 2000))
                .isEqualTo(internal);
        assertInvalid(EM_SPACE.repeat(3), 2000);
    }

    @Test
    void supportsCommentUnicodeCodePointBoundariesThroughTheReusableMaximum() {
        String accepted = EMOJI.repeat(500);
        assertThat(CommunityTextNormalizer.normalize(accepted, "body", 500)).isEqualTo(accepted);
        assertInvalid(EMOJI.repeat(501), 500);
        assertInvalid("x".repeat(501), 500);
        assertThat(CommunityTextNormalizer.normalize("x".repeat(500), "body", 500)).hasSize(500);
    }

    private static void assertInvalid(String value, int maximum) {
        assertThatThrownBy(() -> CommunityTextNormalizer.normalize(value, "body", maximum))
                .isInstanceOf(ApiException.class);
    }
}
