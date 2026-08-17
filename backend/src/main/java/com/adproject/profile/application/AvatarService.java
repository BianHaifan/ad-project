package com.adproject.profile.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.profile.api.AvatarDtos.AvatarMetadata;
import com.adproject.profile.api.AvatarDtos.AvatarResponse;
import com.adproject.profile.infrastructure.UserAvatarEntity;
import com.adproject.profile.infrastructure.UserAvatarRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarService {
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;
    private static final int MAX_AVATAR_PIXELS = 25_000_000;
    private static final int MAX_AVATAR_DIMENSION = 8192;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final UserRepository users;
    private final UserAvatarRepository avatars;
    private final Clock clock;

    public AvatarService(UserRepository users, UserAvatarRepository avatars, Clock clock) {
        this.users = users;
        this.avatars = avatars;
        this.clock = clock;
    }

    @Transactional
    public AvatarResponse upload(AuthenticatedUser principal, MultipartFile file) {
        requireSelf(principal);
        if (file == null || file.isEmpty()) {
            throw validation("file", "is required");
        }
        byte[] content = readBytes(file);
        if (content.length == 0) {
            throw validation("file", "must not be empty");
        }
        if (content.length > MAX_AVATAR_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "Avatar exceeds the 5 MB limit");
        }
        String declared = declaredContentType(file.getContentType());
        if (!isAllowedContentType(declared)) {
            throw validation("file", "must be a PNG or JPEG image");
        }
        String actual = detectContentType(content);
        if (!declared.equals(actual)) {
            throw validation("file", "content does not match its declared type");
        }
        validateDimensions(content);
        validateDecodable(content);

        Instant now = DatabaseTimePrecision.micros(clock.instant());
        UserEntity user = users.findById(principal.userId()).orElseThrow(AvatarService::notFound);
        UserAvatarEntity avatar = avatars.findById(user.getId()).orElse(null);
        if (avatar == null) {
            avatar = new UserAvatarEntity(user.getId(), actual, content.length, content, now, now);
        } else {
            avatar.replace(actual, content.length, content, now);
        }
        avatars.saveAndFlush(avatar);

        String avatarUrl = "/api/v1/avatars/" + user.getId();
        user.updateAvatarUrl(avatarUrl, now);
        users.saveAndFlush(user);
        return new AvatarResponse(new AvatarMetadata(user.getId(), avatarUrl, actual, content.length, now));
    }

    @Transactional
    public void delete(AuthenticatedUser principal) {
        requireSelf(principal);
        Instant now = DatabaseTimePrecision.micros(clock.instant());
        UserEntity user = users.findById(principal.userId()).orElseThrow(AvatarService::notFound);
        avatars.findById(user.getId()).ifPresent(avatars::delete);
        avatars.flush();
        user.updateAvatarUrl(null, now);
        users.saveAndFlush(user);
    }

    @Transactional(readOnly = true)
    public UserAvatarEntity read(String userId) {
        return avatars.findById(userId).orElseThrow(AvatarService::notFound);
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Unable to read the uploaded file");
        }
    }

    private static String declaredContentType(String raw) {
        if (raw == null) {
            return null;
        }
        int separator = raw.indexOf(';');
        String mediaType = (separator >= 0 ? raw.substring(0, separator) : raw).trim().toLowerCase();
        return mediaType.isEmpty() ? null : mediaType;
    }

    private static boolean isAllowedContentType(String contentType) {
        return "image/png".equals(contentType) || "image/jpeg".equals(contentType);
    }

    private static String detectContentType(byte[] content) {
        if (startsWith(content, PNG_SIGNATURE)) {
            return "image/png";
        }
        if (startsWith(content, JPEG_SIGNATURE)) {
            return "image/jpeg";
        }
        return null;
    }

    /**
     * Rejects oversized images from their header metadata before any full decode
     * allocates pixel memory, so a decompression bomb is refused cheaply.
     */
    private static void validateDimensions(byte[] content) {
        int[] dimensions = readDimensions(content);
        if (dimensions == null) {
            throw validation("file", "must be a decodable image");
        }
        int width = dimensions[0];
        int height = dimensions[1];
        if (width <= 0 || height <= 0 || width > MAX_AVATAR_DIMENSION || height > MAX_AVATAR_DIMENSION
                || (long) width * height > MAX_AVATAR_PIXELS) {
            throw validation("file", "image dimensions are not supported");
        }
    }

    /**
     * Reads width/height from the image header without decoding pixels, and releases
     * the reader and input stream. Returns {@code null} when the bytes cannot be parsed
     * as a readable image.
     */
    private static int[] readDimensions(byte[] content) {
        ImageInputStream input = null;
        ImageReader reader = null;
        try {
            input = ImageIO.createImageInputStream(new ByteArrayInputStream(content));
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            reader = readers.next();
            input.seek(0);
            reader.setInput(input, false, true);
            return new int[]{reader.getWidth(0), reader.getHeight(0)};
        } catch (IOException | RuntimeException exception) {
            // Malformed input surfaces as a validation error, never a 500.
            return null;
        } finally {
            if (reader != null) {
                reader.dispose();
            }
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // Nothing to do when closing fails.
                }
            }
        }
    }

    private static void validateDecodable(byte[] content) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException exception) {
            image = null;
        }
        if (image == null) {
            throw validation("file", "must be a decodable image");
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static void requireSelf(AuthenticatedUser principal) {
        if (principal == null || (principal.role() != UserRole.CANDIDATE && principal.role() != UserRole.RECRUITER)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }

    private static ApiException validation(String field, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed",
                Map.of(field, detail));
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Avatar not found");
    }
}
