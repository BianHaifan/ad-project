package com.adproject.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityTask2IntegrationTest {
    private static final String EMOJI = "\uD83D\uDE00";
    private static final String EM_SPACE = "\u2003";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearCommunityData() {
        jdbcTemplate.update("delete from community_post_likes");
        jdbcTemplate.update("delete from community_comments");
        jdbcTemplate.update("delete from community_post_images");
        jdbcTemplate.update("delete from community_posts");
    }

    @Test
    void detailReturnsPersistentDataRealViewerMetricsAndOnlyPublicAuthorFields() throws Exception {
        Account candidate = candidate("detail-author");
        Account recruiter = recruiter("detail-viewer");
        String postId = insertPost(UUID.randomUUID().toString(), candidate.userId(), "Persistent detail",
                "2026-08-11T01:00:00Z");
        insertLike(postId, recruiter.userId());
        insertComment(UUID.randomUUID().toString(), postId, recruiter.userId(), "Comment",
                "2026-08-11T01:01:00Z");

        JsonNode recruiterDetail = detail(recruiter, postId);
        JsonNode candidateDetail = detail(candidate, postId);
        assertThat(recruiterDetail.at("/data/body").asText()).isEqualTo("Persistent detail");
        assertThat(recruiterDetail.at("/data/likeCount").asLong()).isEqualTo(1);
        assertThat(recruiterDetail.at("/data/commentCount").asLong()).isEqualTo(1);
        assertThat(recruiterDetail.at("/data/likedByCurrentUser").asBoolean()).isTrue();
        assertThat(candidateDetail.at("/data/likedByCurrentUser").asBoolean()).isFalse();
        assertThat(candidateDetail.at("/data/body")).isEqualTo(recruiterDetail.at("/data/body"));
        assertThat(recruiterDetail.at("/data/author").size()).isEqualTo(5);
        String json = recruiterDetail.toString();
        for (String privateField : new String[]{"email", "passwordHash", "accessToken", "refreshToken",
                "resume", "verificationStatus"}) {
            assertThat(json).doesNotContain(privateField);
        }
    }

    @Test
    void likeAndUnlikeAreIdempotentAndReturnLatestPerViewerState() throws Exception {
        Account candidate = candidate("like-candidate");
        Account recruiter = recruiter("like-recruiter");
        String postId = insertPost(UUID.randomUUID().toString(), candidate.userId(), "Like me",
                "2026-08-11T02:00:00Z");

        assertInteraction(like(candidate, postId), postId, 1, true);
        assertInteraction(like(candidate, postId), postId, 1, true);
        assertInteraction(like(recruiter, postId), postId, 2, true);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from community_post_likes where post_id = ?", Long.class, postId)).isEqualTo(2);

        assertInteraction(unlike(candidate, postId), postId, 1, false);
        assertInteraction(unlike(candidate, postId), postId, 1, false);
        JsonNode recruiterDetail = detail(recruiter, postId);
        assertThat(recruiterDetail.at("/data/likeCount").asLong()).isEqualTo(1);
        assertThat(recruiterDetail.at("/data/likedByCurrentUser").asBoolean()).isTrue();
    }

    @Test
    void concurrentLikeRequestsBothSucceedAndRelyOnTheUniqueKeyToLeaveOneRow() throws Exception {
        Account candidate = candidate("concurrent-like");
        String postId = insertPost(UUID.randomUUID().toString(), candidate.userId(), "Concurrent",
                "2026-08-11T03:00:00Z");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> likeConcurrent(candidate, postId, ready, start));
            var second = executor.submit(() -> likeConcurrent(candidate, postId, ready, start));
            ready.await();
            start.countDown();
            assertInteraction(first.get(), postId, 1, true);
            assertInteraction(second.get(), postId, 1, true);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from community_post_likes where post_id = ? and user_id = ?",
                Long.class, postId, candidate.userId())).isEqualTo(1);
    }

    @Test
    void commentCreationNormalizesUnicodeAndReturnsLatestCount() throws Exception {
        Account candidate = candidate("comment-author");
        Account recruiter = recruiter("comment-existing");
        String postId = insertPost(UUID.randomUUID().toString(), candidate.userId(), "Discuss",
                "2026-08-11T04:00:00Z");
        insertComment(UUID.randomUUID().toString(), postId, recruiter.userId(), "Existing",
                "2026-08-11T04:01:00Z");
        String normalized = EMOJI.repeat(10) + "left" + EM_SPACE + "middle  right";

        JsonNode created = comment(candidate, postId, "  " + EM_SPACE + normalized + EM_SPACE + "  ");
        assertThat(created.at("/data/comment/body").asText()).isEqualTo(normalized);
        assertThat(created.at("/data/comment/postId").asText()).isEqualTo(postId);
        assertThat(created.at("/data/comment/author/userId").asText()).isEqualTo(candidate.userId());
        assertThat(created.at("/data/commentCount").asLong()).isEqualTo(2);
        String commentId = created.at("/data/comment/id").asText();
        assertThat(jdbcTemplate.queryForObject(
                "select body from community_comments where id = ?", String.class, commentId)).isEqualTo(normalized);
    }

    @Test
    void commentValidationCoversBlankNullBmpAndEmojiBoundaries() throws Exception {
        Account candidate = candidate("comment-validation");
        String postId = insertPost(UUID.randomUUID().toString(), candidate.userId(), "Validate",
                "2026-08-11T05:00:00Z");

        JsonNode accepted = comment(candidate, postId, "x".repeat(500));
        assertThat(accepted.at("/data/comment/body").asText()).hasSize(500);
        for (String value : new String[]{" ", EM_SPACE.repeat(2), "x".repeat(501), EMOJI.repeat(501)}) {
            assertCommentInvalid(candidate, postId, objectMapper.writeValueAsString(Map.of("body", value)));
        }
        assertCommentInvalid(candidate, postId, "{\"body\":null}");
    }

    @Test
    void commentsUseCreatedAtThenIdAscendingDatabasePagination() throws Exception {
        Account candidate = candidate("comment-sort");
        String postId = insertPost(UUID.randomUUID().toString(), candidate.userId(), "Sorted",
                "2026-08-11T06:00:00Z");
        String low = "00000000-0000-0000-0000-000000000001";
        String high = "ffffffff-ffff-ffff-ffff-ffffffffffff";
        insertComment(high, postId, candidate.userId(), "High", "2026-08-11T06:02:00Z");
        insertComment(low, postId, candidate.userId(), "Low", "2026-08-11T06:02:00Z");
        insertComment("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", postId, candidate.userId(), "Earlier",
                "2026-08-11T06:01:00Z");

        JsonNode first = comments(candidate, postId, 1, 2);
        JsonNode second = comments(candidate, postId, 2, 2);
        assertThat(first.at("/data/0/body").asText()).isEqualTo("Earlier");
        assertThat(first.at("/data/1/id").asText()).isEqualTo(low);
        assertThat(second.at("/data/0/id").asText()).isEqualTo(high);
        assertThat(first.at("/meta/total").asLong()).isEqualTo(3);
        assertThat(first.at("/meta/hasNext").asBoolean()).isTrue();
        assertThat(second.at("/meta/hasNext").asBoolean()).isFalse();
    }

    @Test
    void commentsDefaultPaginationSupportsMaximumAndIsIdenticalAcrossAccounts() throws Exception {
        Account candidate = candidate("comments-candidate");
        Account recruiter = recruiter("comments-recruiter");
        String postId = insertPost(UUID.randomUUID().toString(), candidate.userId(), "Shared comments",
                "2026-08-11T07:00:00Z");
        insertComment(UUID.randomUUID().toString(), postId, recruiter.userId(), "Shared",
                "2026-08-11T07:01:00Z");

        JsonNode candidatePage = comments(candidate, postId, null, null);
        JsonNode recruiterPage = comments(recruiter, postId, 1, 50);
        assertThat(candidatePage.at("/meta/page").asInt()).isEqualTo(1);
        assertThat(candidatePage.at("/meta/pageSize").asInt()).isEqualTo(20);
        assertThat(recruiterPage.at("/meta/pageSize").asInt()).isEqualTo(50);
        assertThat(candidatePage.get("data")).isEqualTo(recruiterPage.get("data"));
        assertThat(candidatePage.at("/data/0/author").size()).isEqualTo(5);
    }

    @Test
    void emptyCommentsAndInvalidPaginationUseTheApprovedEnvelope() throws Exception {
        Account candidate = candidate("empty-comments");
        String postId = insertPost(UUID.randomUUID().toString(), candidate.userId(), "Empty",
                "2026-08-11T08:00:00Z");
        assertThat(comments(candidate, postId, null, null).at("/data").isEmpty()).isTrue();

        for (String[] query : new String[][]{{"page", "0"}, {"pageSize", "0"}, {"pageSize", "51"}}) {
            mockMvc.perform(get("/api/v1/community/posts/{postId}/comments", postId)
                            .queryParam(query[0], query[1]).header("Authorization", bearer(candidate)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.requestId").isNotEmpty());
        }
    }

    @Test
    void everyTask2OperationReturnsNotFoundForAMissingPost() throws Exception {
        Account candidate = candidate("missing-post");
        String missing = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/v1/community/posts/{postId}", missing).header("Authorization", bearer(candidate)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mockMvc.perform(put("/api/v1/community/posts/{postId}/like", missing)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/community/posts/{postId}/like", missing)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/community/posts/{postId}/comments", missing)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mockMvc.perform(post("/api/v1/community/posts/{postId}/comments", missing)
                        .header("Authorization", bearer(candidate)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Missing\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void everyTask2OperationRequiresAuthenticationWithTheExistingEnvelope() throws Exception {
        String postId = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(put("/api/v1/community/posts/{postId}/like", postId))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(delete("/api/v1/community/posts/{postId}/like", postId))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/community/posts/{postId}/comments", postId))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/community/posts/{postId}/comments", postId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"No token\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    private JsonNode likeConcurrent(Account account, String postId, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        start.await();
        return like(account, postId);
    }

    private JsonNode detail(Account account, String postId) throws Exception {
        return read(mockMvc.perform(get("/api/v1/community/posts/{postId}", postId)
                        .header("Authorization", bearer(account)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode like(Account account, String postId) throws Exception {
        return read(mockMvc.perform(put("/api/v1/community/posts/{postId}/like", postId)
                        .header("Authorization", bearer(account)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode unlike(Account account, String postId) throws Exception {
        return read(mockMvc.perform(delete("/api/v1/community/posts/{postId}/like", postId)
                        .header("Authorization", bearer(account)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode comment(Account account, String postId, String body) throws Exception {
        return read(mockMvc.perform(post("/api/v1/community/posts/{postId}/comments", postId)
                        .header("Authorization", bearer(account)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", body))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode comments(Account account, String postId, Integer page, Integer pageSize) throws Exception {
        var request = get("/api/v1/community/posts/{postId}/comments", postId)
                .header("Authorization", bearer(account));
        if (page != null) request.queryParam("page", page.toString());
        if (pageSize != null) request.queryParam("pageSize", pageSize.toString());
        return read(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void assertCommentInvalid(Account account, String postId, String payload) throws Exception {
        mockMvc.perform(post("/api/v1/community/posts/{postId}/comments", postId)
                        .header("Authorization", bearer(account)).contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.body").exists());
    }

    private static void assertInteraction(JsonNode response, String postId, long count, boolean liked) {
        assertThat(response.at("/data/postId").asText()).isEqualTo(postId);
        assertThat(response.at("/data/likeCount").asLong()).isEqualTo(count);
        assertThat(response.at("/data/likedByCurrentUser").asBoolean()).isEqualTo(liked);
    }

    private String insertPost(String id, String authorId, String body, String createdAt) {
        Timestamp time = Timestamp.from(Instant.parse(createdAt));
        jdbcTemplate.update("insert into community_posts (id,author_id,body,created_at,updated_at) values (?,?,?,?,?)",
                id, authorId, body, time, time);
        return id;
    }

    private void insertLike(String postId, String userId) {
        jdbcTemplate.update("insert into community_post_likes (post_id,user_id,created_at) values (?,?,?)",
                postId, userId, Timestamp.from(Instant.parse("2026-08-11T00:00:00Z")));
    }

    private void insertComment(String id, String postId, String authorId, String body, String createdAt) {
        Timestamp time = Timestamp.from(Instant.parse(createdAt));
        jdbcTemplate.update("insert into community_comments (id,post_id,author_id,body,created_at,updated_at) "
                + "values (?,?,?,?,?,?)", id, postId, authorId, body, time, time);
    }

    private Account recruiter(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String companyName = prefix + " Company";
        JsonNode response = register("""
                {"role":"RECRUITER","companyName":"%s","fullName":"Recruiter One",
                 "email":"%s","password":"StrongPass123!","acceptedTermsVersion":"2026-08"}
                """.formatted(companyName, email));
        return new Account(response.at("/data/accessToken").asText(), response.at("/data/user/userId").asText());
    }

    private Account candidate(String prefix) throws Exception {
        JsonNode response = register("""
                {"role":"CANDIDATE","fullName":"Candidate One","email":"%s",
                 "password":"StrongPass123!","acceptedTermsVersion":"2026-08"}
                """.formatted(uniqueEmail(prefix)));
        return new Account(response.at("/data/accessToken").asText(), response.at("/data/user/userId").asText());
    }

    private JsonNode register(String body) throws Exception {
        return read(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode read(String body) throws Exception { return objectMapper.readTree(body); }
    private static String bearer(Account account) { return "Bearer " + account.accessToken(); }
    private static String uniqueEmail(String prefix) { return prefix + "-" + UUID.randomUUID() + "@example.com"; }
    private record Account(String accessToken, String userId) {}
}
