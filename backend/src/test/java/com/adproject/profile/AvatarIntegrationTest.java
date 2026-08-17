package com.adproject.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adproject.auth.application.JwtService;
import com.adproject.company.domain.CompanyMemberRole;
import com.adproject.company.domain.CompanyVerificationStatus;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvatarIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired CompanyMemberRepository members;
    @Autowired JwtService jwt;

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    void candidateUploadsReadsAndDeletesAvatar() throws Exception {
        UserEntity candidate = createCandidate("candidate-avatar");
        byte[] png = pngBytes();

        mvc.perform(multipart("/api/v1/profile/avatar").file(pngFile(png))
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(candidate.getId()))
                .andExpect(jsonPath("$.data.avatarUrl").value("/api/v1/avatars/" + candidate.getId()))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.sizeBytes").value(png.length));

        mvc.perform(get("/api/v1/avatars/{userId}", candidate.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().longValue("Content-Length", png.length))
                .andExpect(content().bytes(png));

        mvc.perform(delete("/api/v1/profile/avatar").header("Authorization", bearer(candidate)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/avatars/{userId}", candidate.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void recruiterUploadsReadsAndDeletesAvatar() throws Exception {
        UserEntity recruiter = createRecruiter("recruiter-avatar");
        byte[] jpeg = jpegBytes();

        mvc.perform(multipart("/api/v1/profile/avatar").file(jpegFile(jpeg))
                        .header("Authorization", bearer(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("/api/v1/avatars/" + recruiter.getId()))
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"));

        mvc.perform(get("/api/v1/avatars/{userId}", recruiter.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().longValue("Content-Length", jpeg.length))
                .andExpect(content().bytes(jpeg));

        mvc.perform(delete("/api/v1/profile/avatar").header("Authorization", bearer(recruiter)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/avatars/{userId}", recruiter.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadAndDeleteRequireAuthentication() throws Exception {
        mvc.perform(multipart("/api/v1/profile/avatar").file(pngFile(pngBytes())))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/profile/avatar"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readIsPublicAndReturnsUniform404ForUnknownUser() throws Exception {
        mvc.perform(get("/api/v1/avatars/{userId}", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnsupportedDeclaredType() throws Exception {
        UserEntity candidate = createCandidate("candidate-unsupported");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.svg", "image/svg+xml",
                "<svg/>".getBytes());
        mvc.perform(multipart("/api/v1/profile/avatar").file(file)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsForgedMime() throws Exception {
        UserEntity candidate = createCandidate("candidate-forged");
        // Declares PNG but the bytes are a valid JPEG.
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", jpegBytes());
        mvc.perform(multipart("/api/v1/profile/avatar").file(file)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsFakeImage() throws Exception {
        UserEntity candidate = createCandidate("candidate-fake");
        // Declares PNG but the bytes are plain text with a PNG signature prefix removed.
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png",
                "definitely not an image".getBytes());
        mvc.perform(multipart("/api/v1/profile/avatar").file(file)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsCorruptImage() throws Exception {
        UserEntity candidate = createCandidate("candidate-corrupt");
        // Valid PNG signature followed by garbage is not a decodable image.
        byte[] corrupt = new byte[PNG_SIGNATURE.length + 64];
        System.arraycopy(PNG_SIGNATURE, 0, corrupt, 0, PNG_SIGNATURE.length);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", corrupt);
        mvc.perform(multipart("/api/v1/profile/avatar").file(file)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        UserEntity candidate = createCandidate("candidate-empty");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[0]);
        mvc.perform(multipart("/api/v1/profile/avatar").file(file)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsOversizedFile() throws Exception {
        UserEntity candidate = createCandidate("candidate-oversized");
        byte[] oversized = new byte[(5 * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", oversized);
        mvc.perform(multipart("/api/v1/profile/avatar").file(file)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void rejectsOversizedPngDimensionsBeforeFullDecode() throws Exception {
        UserEntity candidate = createCandidate("candidate-dims-png");
        // A valid PNG header declaring 20000x20000 with no pixel data: the header
        // dimension check must reject it without ever decoding pixels.
        byte[] huge = minimalPngWithDimensions(20000, 20000);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", huge);
        mvc.perform(multipart("/api/v1/profile/avatar").file(file)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.file").value("image dimensions are not supported"));
    }

    @Test
    void rejectsOversizedJpegDimensionsBeforeFullDecode() throws Exception {
        UserEntity candidate = createCandidate("candidate-dims-jpeg");
        byte[] huge = jpegWithDimensions(20000, 20000);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", huge);
        mvc.perform(multipart("/api/v1/profile/avatar").file(file)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deletingOwnAvatarDoesNotAffectAnotherUser() throws Exception {
        UserEntity first = createCandidate("candidate-owner");
        UserEntity second = createCandidate("candidate-other");
        byte[] png = pngBytes();

        mvc.perform(multipart("/api/v1/profile/avatar").file(pngFile(png))
                        .header("Authorization", bearer(first)))
                .andExpect(status().isOk());

        // Second user deletes their own (absent) avatar; the first user's avatar must survive.
        mvc.perform(delete("/api/v1/profile/avatar").header("Authorization", bearer(second)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/avatars/{userId}", first.getId()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(png));
    }

    @Test
    void noTargetUserIdIsAcceptedForUploadOrDelete() throws Exception {
        UserEntity owner = createCandidate("candidate-owner2");
        UserEntity other = createCandidate("candidate-other2");
        byte[] png = pngBytes();

        mvc.perform(multipart("/api/v1/profile/avatar").file(pngFile(png))
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk());

        // A forged target user ID (query param) must be ignored: delete always targets self.
        mvc.perform(delete("/api/v1/profile/avatar")
                        .param("userId", owner.getId())
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/avatars/{userId}", owner.getId()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(png));
    }

    private MockMultipartFile pngFile(byte[] png) {
        return new MockMultipartFile("file", "avatar.png", "image/png", png);
    }

    private MockMultipartFile jpegFile(byte[] jpeg) {
        return new MockMultipartFile("file", "avatar.jpg", "image/jpeg", jpeg);
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpegBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    /** A PNG with a valid signature + IHDR declaring width/height but no pixel data. */
    private static byte[] minimalPngWithDimensions(int width, int height) throws Exception {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] typeAndData = new byte[17];
        System.arraycopy("IHDR".getBytes(StandardCharsets.US_ASCII), 0, typeAndData, 0, 4);
        putInt(typeAndData, 4, width);
        putInt(typeAndData, 8, height);
        typeAndData[12] = 8;  // bit depth
        typeAndData[13] = 2;  // color type: truecolor RGB
        typeAndData[14] = 0;  // compression
        typeAndData[15] = 0;  // filter
        typeAndData[16] = 0;  // interlace

        CRC32 crc = new CRC32();
        crc.update(typeAndData);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(signature);
        out.write(intBytes(13));
        out.write(typeAndData);
        out.write(intBytes((int) crc.getValue()));
        return out.toByteArray();
    }

    /** A JPEG with SOI + SOF0 declaring width/height but no scan data. */
    private static byte[] jpegWithDimensions(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF); out.write(0xD8);       // SOI
        out.write(0xFF); out.write(0xC0);       // SOF0 marker
        out.write(0x00); out.write(17);         // length = 8 + 3 * 3 components
        out.write(8);                           // precision
        out.write((height >> 8) & 0xFF); out.write(height & 0xFF);  // height
        out.write((width >> 8) & 0xFF); out.write(width & 0xFF);    // width
        out.write(3);                           // 3 components
        for (int i = 1; i <= 3; i++) {
            out.write(i);                       // component id
            out.write(0x11);                    // sampling 4:4:4
            out.write(0);                       // quant table 0
        }
        return out.toByteArray();
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static byte[] intBytes(int value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    private UserEntity createCandidate(String prefix) {
        return users.save(new UserEntity(UUID.randomUUID().toString(),
                prefix + "-" + UUID.randomUUID() + "@example.com", "hash", "Candidate One",
                UserRole.CANDIDATE, UserStatus.ACTIVE, "2026-08", NOW, NOW));
    }

    private UserEntity createRecruiter(String prefix) {
        UserEntity user = users.save(new UserEntity(UUID.randomUUID().toString(),
                prefix + "-" + UUID.randomUUID() + "@example.com", "hash", "Recruiter One",
                UserRole.RECRUITER, UserStatus.ACTIVE, "2026-08", NOW, NOW));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(),
                "Example Labs", CompanyVerificationStatus.APPROVED, 1, user.getId(), NOW, NOW));
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(), user.getId(),
                CompanyMemberRole.ADMIN, NOW));
        return user;
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwt.createAccessToken(user);
    }
}
