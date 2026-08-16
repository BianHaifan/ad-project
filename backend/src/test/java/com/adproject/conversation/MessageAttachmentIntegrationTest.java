package com.adproject.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.SalaryCurrency;
import com.adproject.job.domain.SalaryPeriod;
import com.adproject.job.domain.Visibility;
import com.adproject.job.domain.WorkplaceType;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class MessageAttachmentIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JwtService jwt; @Autowired UserRepository users;
    @Autowired CompanyRepository companies; @Autowired CompanyMemberRepository members;
    @Autowired JobRepository jobs; @Autowired ResumeRepository resumes; @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test void recruiterUploadsAndCandidateDownloadsAttachment() throws Exception {
        Fixture f = fixture("Download Candidate");
        String conversationId = conversation(f);
        byte[] content = pdf("candidate resume content");

        String messageId = upload(recruiter(f), conversationId, "resume.pdf", content, "Please review my resume");

        assertThat(jdbc.queryForObject("select count(*) from message_attachments where message_id=?",
                Integer.class, messageId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select content_type from message_attachments where message_id=?",
                String.class, messageId)).isEqualTo("application/pdf");

        mvc.perform(get("/api/v1/candidate/conversations/{id}/messages/{mid}/attachment", conversationId, messageId)
                        .header("Authorization", candidate(f)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        MvcResult download = mvc.perform(get(
                        "/api/v1/candidate/conversations/{id}/messages/{mid}/attachment", conversationId, messageId)
                        .header("Authorization", candidate(f)))
                .andExpect(status().isOk()).andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(content);
    }

    @Test void candidateUploadsAttachmentAndRecruiterDownloads() throws Exception {
        Fixture f = fixture("Candidate Upload Candidate");
        String conversationId = conversation(f);
        byte[] content = pdf("candidate notes");

        String response = mvc.perform(multipart("/api/v1/candidate/conversations/{id}/messages/attachment", conversationId)
                        .file(new MockMultipartFile("file", "notes.pdf", "application/octet-stream", content))
                        .param("clientMessageId", UUID.randomUUID().toString())
                        .param("body", "My notes")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Authorization", candidate(f)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attachment.fileName").value("notes.pdf"))
                .andExpect(jsonPath("$.data.attachment.contentType").value("application/pdf"))
                .andReturn().getResponse().getContentAsString();
        String messageId = mapper.readTree(response).at("/data/messageId").asText();

        MvcResult download = mvc.perform(get(
                        "/api/v1/recruiter/conversations/{id}/messages/{mid}/attachment", conversationId, messageId)
                        .header("Authorization", recruiter(f)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(content);
    }

    @Test void attachmentOnlyMessageIsSendable() throws Exception {
        Fixture f = fixture("Attachment Only Candidate");
        String conversationId = conversation(f);

        String messageId = upload(recruiter(f), conversationId, "offer.txt", "attachment only".getBytes(StandardCharsets.UTF_8), null);

        mvc.perform(get("/api/v1/candidate/conversations/{id}/messages", conversationId)
                        .header("Authorization", candidate(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].messageId").value(messageId))
                .andExpect(jsonPath("$.data[0].body").value(""))
                .andExpect(jsonPath("$.data[0].attachment.fileName").value("offer.txt"))
                .andExpect(jsonPath("$.data[0].attachment.sizeBytes").value(15))
                .andExpect(jsonPath("$.data[0].attachment.contentType").value("text/plain"))
                .andExpect(jsonPath("$.data[0].attachment.content").doesNotExist());
    }

    @Test void messageResponseExposesMetadataNotBinaryContent() throws Exception {
        Fixture f = fixture("Metadata Candidate");
        String conversationId = conversation(f);

        upload(recruiter(f), conversationId, "photo.png", png(), "An image");

        mvc.perform(get("/api/v1/recruiter/conversations/{id}/messages", conversationId)
                        .header("Authorization", recruiter(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].attachment.attachmentId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].attachment.fileName").value("photo.png"))
                .andExpect(jsonPath("$.data[0].attachment.sizeBytes").value(png().length))
                .andExpect(jsonPath("$.data[0].attachment.contentType").value("image/png"))
                .andExpect(jsonPath("$.data[0].attachment.content").doesNotExist());
    }

    @Test void rejectsDisallowedFileType() throws Exception {
        Fixture f = fixture("Wrong Type Candidate");
        String conversationId = conversation(f);

        mvc.perform(attachmentRequest(recruiter(f), conversationId, "malware.exe",
                        "any bytes".getBytes(StandardCharsets.UTF_8), "text"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(attachmentCount(conversationId)).isZero();
    }

    @Test void rejectsContentThatDoesNotMatchDeclaredType() throws Exception {
        Fixture f = fixture("Magic Byte Candidate");
        String conversationId = conversation(f);

        // Named .pdf but content is not a PDF.
        mvc.perform(attachmentRequest(recruiter(f), conversationId, "fake.pdf",
                        "definitely not a pdf".getBytes(StandardCharsets.UTF_8), "text"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(attachmentCount(conversationId)).isZero();
    }

    @Test void rejectsOversizeFile() throws Exception {
        Fixture f = fixture("Oversize Candidate");
        String conversationId = conversation(f);
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        big[0] = '%'; big[1] = 'P'; big[2] = 'D'; big[3] = 'F'; big[4] = '-';

        mvc.perform(attachmentRequest(recruiter(f), conversationId, "big.pdf", big, "text"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));
        assertThat(attachmentCount(conversationId)).isZero();
    }

    @Test void uploadAndDownloadRequireAuthentication() throws Exception {
        Fixture f = fixture("Unauth Candidate");
        String conversationId = conversation(f);
        String messageId = upload(recruiter(f), conversationId, "a.pdf", pdf("x"), "text");

        mvc.perform(attachmentRequest(null, conversationId, "a.pdf", pdf("x"), "text"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/candidate/conversations/{id}/messages/{mid}/attachment", conversationId, messageId))
                .andExpect(status().isUnauthorized());
    }

    @Test void wrongRoleCannotUploadOrDownload() throws Exception {
        Fixture f = fixture("Wrong Role Candidate");
        String conversationId = conversation(f);
        String messageId = upload(recruiter(f), conversationId, "a.pdf", pdf("x"), "text");

        // Candidate cannot use the recruiter upload/download endpoints, and vice versa.
        mvc.perform(attachmentRequest(candidate(f), conversationId, "a.pdf", pdf("x"), "text"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/recruiter/conversations/{id}/messages/{mid}/attachment", conversationId, messageId)
                        .header("Authorization", candidate(f)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/candidate/conversations/{id}/messages/{mid}/attachment", conversationId, messageId)
                        .header("Authorization", recruiter(f)))
                .andExpect(status().isForbidden());
    }

    @Test void nonParticipantCannotUploadOrDownload() throws Exception {
        Fixture owner = fixture("Owner Candidate");
        String conversationId = conversation(owner);
        String messageId = upload(recruiter(owner), conversationId, "a.pdf", pdf("x"), "text");
        Fixture other = fixture("Other Candidate");

        // A different candidate is not a participant.
        mvc.perform(get("/api/v1/candidate/conversations/{id}/messages/{mid}/attachment", conversationId, messageId)
                        .header("Authorization", candidate(other)))
                .andExpect(status().isNotFound());

        // A recruiter from another company cannot see the conversation.
        mvc.perform(get("/api/v1/recruiter/conversations/{id}/messages/{mid}/attachment", conversationId, messageId)
                        .header("Authorization", recruiter(other)))
                .andExpect(status().isNotFound());
    }

    @Test void uploadIsIdempotentAndDetectsReusedKey() throws Exception {
        Fixture f = fixture("Idempotent Attachment Candidate");
        String conversationId = conversation(f);
        byte[] content = pdf("contract");

        String key = UUID.randomUUID().toString();
        String clientMessageId = UUID.randomUUID().toString();
        String first = upload(recruiter(f), conversationId, key, clientMessageId, "contract.pdf", content, "sign please");

        // Exact replay returns the same message, no duplicate rows.
        String replay = upload(recruiter(f), conversationId, key, clientMessageId, "contract.pdf", content, "sign please");
        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from messages where conversation_id=?",
                Integer.class, conversationId)).isEqualTo(1);
        assertThat(attachmentCount(conversationId)).isEqualTo(1);

        // Same key but different attachment must be rejected.
        mvc.perform(attachmentRequest(recruiter(f), conversationId, key, clientMessageId, "contract.pdf",
                        pdf("different content"), "sign please"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(jdbc.queryForObject("select count(*) from messages where conversation_id=?",
                Integer.class, conversationId)).isEqualTo(1);
        assertThat(attachmentCount(conversationId)).isEqualTo(1);
    }

    @Test void acceptsValidDocAttachment() throws Exception {
        Fixture f = fixture("Valid Doc Candidate");
        String conversationId = conversation(f);
        upload(recruiter(f), conversationId, "resume.doc", oleDoc(), "My doc");
        assertThat(attachmentCount(conversationId)).isEqualTo(1);
    }

    @Test void acceptsValidDocxAttachment() throws Exception {
        Fixture f = fixture("Valid Docx Candidate");
        String conversationId = conversation(f);
        upload(recruiter(f), conversationId, "resume.docx", validDocx(), "My docx");
        assertThat(attachmentCount(conversationId)).isEqualTo(1);
    }

    @Test void acceptsValidTxtAttachment() throws Exception {
        Fixture f = fixture("Valid Txt Candidate");
        String conversationId = conversation(f);
        upload(recruiter(f), conversationId, "notes.txt",
                "hello\nworld\tok".getBytes(StandardCharsets.UTF_8), "My notes");
        assertThat(attachmentCount(conversationId)).isEqualTo(1);
    }

    @Test void rejectsFakeDocWithWrongHeader() throws Exception {
        Fixture f = fixture("Fake Doc Candidate");
        String conversationId = conversation(f);
        mvc.perform(attachmentRequest(recruiter(f), conversationId, "fake.doc",
                        "not an ole compound file".getBytes(StandardCharsets.UTF_8), "text"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(attachmentCount(conversationId)).isZero();
    }

    @Test void rejectsPlainZipDisguisedAsDocx() throws Exception {
        Fixture f = fixture("Fake Docx Candidate");
        String conversationId = conversation(f);
        mvc.perform(attachmentRequest(recruiter(f), conversationId, "fake.docx", plainZip(), "text"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(attachmentCount(conversationId)).isZero();
    }

    @Test void rejectsInvalidUtf8Txt() throws Exception {
        Fixture f = fixture("Invalid Txt Candidate");
        String conversationId = conversation(f);
        mvc.perform(attachmentRequest(recruiter(f), conversationId, "bad.txt",
                        new byte[]{0x48, 0x69, (byte) 0xFF}, "text"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(attachmentCount(conversationId)).isZero();
    }

    @Test void rejectsTxtWithNulByte() throws Exception {
        Fixture f = fixture("Nul Txt Candidate");
        String conversationId = conversation(f);
        mvc.perform(attachmentRequest(recruiter(f), conversationId, "bad.txt",
                        new byte[]{0x41, 0x00, 0x42}, "text"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(attachmentCount(conversationId)).isZero();
    }

    // ---- helpers ----

    private String conversation(Fixture f) throws Exception {
        String jobId = job(f, "Attachment Job");
        String response = mvc.perform(post("/api/v1/jobs/{id}/applications", jobId)
                        .header("Authorization", candidate(f)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"" + f.resumeId() + "\",\"contactEmail\":\"" + f.email()
                                + "\",\"shareProfile\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String applicationId = mapper.readTree(response).at("/data/applicationId").asText();
        return jdbc.queryForObject("select id from conversations where application_id=?", String.class, applicationId);
    }

    private String upload(String token, String conversationId, String fileName, byte[] content, String body)
            throws Exception {
        return upload(token, conversationId, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                fileName, content, body);
    }

    private String upload(String token, String conversationId, String key, String clientMessageId,
                          String fileName, byte[] content, String body) throws Exception {
        String response = mvc.perform(attachmentRequest(token, conversationId, key, clientMessageId, fileName, content, body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/messageId").asText();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder attachmentRequest(
            String token, String conversationId, String fileName, byte[] content, String body) {
        return attachmentRequest(token, conversationId, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                fileName, content, body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder attachmentRequest(
            String token, String conversationId, String key, String clientMessageId,
            String fileName, byte[] content, String body) {
        var request = multipart("/api/v1/recruiter/conversations/{id}/messages/attachment", conversationId)
                .file(new MockMultipartFile("file", fileName, "application/octet-stream", content))
                .param("clientMessageId", clientMessageId)
                .header("Idempotency-Key", key);
        if (body != null) {
            request.param("body", body);
        }
        if (token != null) {
            request.header("Authorization", token);
        }
        return request;
    }

    private int attachmentCount(String conversationId) {
        return jdbc.queryForObject("select count(*) from message_attachments a join messages m on a.message_id = m.id "
                + "where m.conversation_id=?", Integer.class, conversationId);
    }

    private String job(Fixture f, String title) {
        Instant now = Instant.parse("2026-08-14T03:00:00Z");
        String id = UUID.randomUUID().toString();
        jobs.save(new JobEntity(id, f.companyId(), f.recruiterId(), f.recruiterId(), title, EmploymentType.FULL_TIME,
                WorkplaceType.HYBRID, "Singapore", 5000, 8000, SalaryCurrency.SGD, SalaryPeriod.MONTH, "Description",
                "[]", "[]", null, Visibility.PUBLIC, JobStatus.ACTIVE, 0, 1, now, now));
        return id;
    }

    private Fixture fixture(String candidateName) {
        Instant now = Instant.parse("2026-08-14T03:00:00Z");
        UserEntity recruiter = users.save(user("Recruiter", UserRole.RECRUITER, now));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(), "Company",
                CompanyVerificationStatus.APPROVED, 1, recruiter.getId(), now, now));
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(), recruiter.getId(),
                CompanyMemberRole.ADMIN, now));
        UserEntity candidate = users.save(user(candidateName, UserRole.CANDIDATE, now));
        String resumeId = UUID.randomUUID().toString();
        resumes.save(new ResumeEntity(resumeId, candidate.getId(), candidateName, 28, "Singapore", "Engineer", "Summary",
                "[]", 1, now, now));
        return new Fixture(jwt.createAccessToken(recruiter), jwt.createAccessToken(candidate),
                recruiter.getId(), candidate.getId(), candidate.getEmail(), company.getId(), resumeId);
    }

    private UserEntity user(String name, UserRole role, Instant now) {
        String id = UUID.randomUUID().toString();
        return new UserEntity(id, id + "@example.com", "hash", name, role, UserStatus.ACTIVE, "2026-08", now, now);
    }

    private static byte[] pdf(String body) {
        return ("%PDF-1.4\n" + body).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    }

    private static byte[] oleDoc() {
        byte[] header = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        byte[] body = "fake ole body".getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[header.length + body.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(body, 0, out, header.length, body.length);
        return out;
    }

    private static byte[] validDocx() {
        return zip(Map.of(
                "[Content_Types].xml", "<Types/>".getBytes(StandardCharsets.UTF_8),
                "word/document.xml", "<document/>".getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] plainZip() {
        return zip(Map.of("hello.txt", "hello".getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] zip(Map<String, byte[]> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to build test zip", exception);
        }
        return out.toByteArray();
    }

    private static String candidate(Fixture f) { return "Bearer " + f.candidateToken(); }
    private static String recruiter(Fixture f) { return "Bearer " + f.recruiterToken(); }

    private record Fixture(String recruiterToken, String candidateToken, String recruiterId, String candidateId,
                           String email, String companyId, String resumeId) {}
}
