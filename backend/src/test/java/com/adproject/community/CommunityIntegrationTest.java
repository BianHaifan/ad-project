package com.adproject.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
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
class CommunityIntegrationTest {
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
    void candidateAndRecruiterCreateTrimmedPostsWithPublicAuthors() throws Exception {
        Account candidate = candidate("create-candidate");
        Account recruiter = recruiter("create-recruiter");

        JsonNode candidatePost = create(candidate, "  Candidate post  ");
        JsonNode recruiterPost = create(recruiter, "Recruiter post");

        assertThat(candidatePost.at("/data/body").asText()).isEqualTo("Candidate post");
        assertThat(candidatePost.at("/data/author/userId").asText()).isEqualTo(candidate.userId());
        assertThat(candidatePost.at("/data/author/role").asText()).isEqualTo("CANDIDATE");
        assertThat(candidatePost.at("/data/author/avatarUrl").isNull()).isTrue();
        assertThat(candidatePost.at("/data/author/companyName").isNull()).isTrue();
        assertThat(recruiterPost.at("/data/author/role").asText()).isEqualTo("RECRUITER");
        assertThat(recruiterPost.at("/data/author/companyName").asText()).isEqualTo(recruiter.companyName());
        assertThat(recruiterPost.at("/data/likeCount").asLong()).isZero();
        assertThat(recruiterPost.at("/data/commentCount").asLong()).isZero();
        assertThat(recruiterPost.at("/data/likedByCurrentUser").asBoolean()).isFalse();
    }

    @Test
    void createdPostAppearsFirstAndBothRolesReadTheSameFeed() throws Exception {
        Account candidate = candidate("shared-feed-candidate");
        Account recruiter = recruiter("shared-feed-recruiter");
        insertPost("00000000-0000-0000-0000-000000000001", candidate.userId(), "Older",
                "2026-08-10T01:00:00Z");
        String createdId = create(recruiter, "Newest").at("/data/id").asText();

        JsonNode candidateFeed = feed(candidate, null, null);
        JsonNode recruiterFeed = feed(recruiter, null, null);
        assertThat(candidateFeed.at("/data/0/id").asText()).isEqualTo(createdId);
        assertThat(candidateFeed.get("data")).isEqualTo(recruiterFeed.get("data"));
        assertThat(candidateFeed.at("/meta/page").asInt()).isEqualTo(1);
        assertThat(candidateFeed.at("/meta/pageSize").asInt()).isEqualTo(20);
        assertThat(candidateFeed.at("/meta/total").asLong()).isEqualTo(2);
        assertThat(candidateFeed.at("/meta/hasNext").asBoolean()).isFalse();
    }

    @Test
    void feedUsesCreatedAtThenIdDescendingStablePaginationAndSupportsMaximumPageSize() throws Exception {
        Account candidate = candidate("stable-feed");
        String sameTime = "2026-08-10T02:00:00Z";
        String low = "00000000-0000-0000-0000-000000000001";
        String high = "ffffffff-ffff-ffff-ffff-ffffffffffff";
        insertPost(low, candidate.userId(), "Low", sameTime);
        insertPost(high, candidate.userId(), "High", sameTime);
        insertPost("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", candidate.userId(), "Old", "2026-08-09T02:00:00Z");

        JsonNode first = feed(candidate, 1, 1);
        JsonNode second = feed(candidate, 2, 1);
        assertThat(first.at("/data/0/id").asText()).isEqualTo(high);
        assertThat(second.at("/data/0/id").asText()).isEqualTo(low);
        assertThat(first.at("/meta/hasNext").asBoolean()).isTrue();

        JsonNode maximum = feed(candidate, 1, 50);
        assertThat(maximum.at("/meta/pageSize").asInt()).isEqualTo(50);
        assertThat(maximum.at("/data").size()).isEqualTo(3);
    }

    @Test
    void emptyFeedAndPaginationValidationUseApprovedEnvelope() throws Exception {
        Account candidate = candidate("empty-feed");
        JsonNode empty = feed(candidate, null, null);
        assertThat(empty.at("/data").isEmpty()).isTrue();
        assertThat(empty.at("/meta/total").asLong()).isZero();

        for (String[] query : new String[][]{{"page", "0"}, {"pageSize", "0"}, {"pageSize", "51"}}) {
            mockMvc.perform(get("/api/v1/community/posts").queryParam(query[0], query[1])
                            .header("Authorization", bearer(candidate)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void bodyValidationRejectsBlankNullAndOverlongValues() throws Exception {
        Account candidate = candidate("body-validation");
        for (String body : new String[]{"{\"body\":\"   \"}", "{\"body\":null}",
                "{\"body\":\"" + "x".repeat(2001) + "\"}"}) {
            mockMvc.perform(post("/api/v1/community/posts").header("Authorization", bearer(candidate))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.fieldErrors.body").exists());
        }
    }

    @Test
    void unicodeBodyIsStrippedWithoutChangingInternalWhitespaceAndPersistsTheNormalizedValue() throws Exception {
        Account candidate = candidate("unicode-body");
        String emoji = "\uD83D\uDE00";
        String accepted = emoji.repeat(10) + "left\u2003middle  right";
        JsonNode created = create(candidate, "\u2003" + accepted + "\u2003");
        String postId = created.at("/data/id").asText();

        assertThat(created.at("/data/body").asText()).isEqualTo(accepted);
        assertThat(jdbcTemplate.queryForObject("select body from community_posts where id = ?", String.class, postId))
                .isEqualTo(accepted);

        mockMvc.perform(post("/api/v1/community/posts").header("Authorization", bearer(candidate))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("body", emoji.repeat(2001)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fieldErrors.body").exists());
    }

    @Test
    void communityRequiresAuthenticationForReadAndWrite() throws Exception {
        mockMvc.perform(get("/api/v1/community/posts").header("X-Request-Id", "req_community_unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(jsonPath("$.error.requestId").value("req_community_unauthorized"));
        mockMvc.perform(post("/api/v1/community/posts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"No token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test
    void feedReturnsRealInteractionCountsAndViewerLikeState() throws Exception {
        Account candidate = candidate("metrics-author");
        Account recruiter = recruiter("metrics-viewer");
        String postId = insertPost(UUID.randomUUID().toString(), candidate.userId(), "Counted",
                "2026-08-10T03:00:00Z");
        jdbcTemplate.update("insert into community_post_likes (post_id,user_id,created_at) values (?,?,?)",
                postId, recruiter.userId(), Timestamp.from(Instant.parse("2026-08-10T03:01:00Z")));
        jdbcTemplate.update("insert into community_comments (id,post_id,author_id,body,created_at,updated_at) "
                        + "values (?,?,?,?,?,?)", UUID.randomUUID().toString(), postId, recruiter.userId(), "Real comment",
                Timestamp.from(Instant.parse("2026-08-10T03:02:00Z")),
                Timestamp.from(Instant.parse("2026-08-10T03:02:00Z")));

        JsonNode recruiterFeed = feed(recruiter, null, null);
        JsonNode candidateFeed = feed(candidate, null, null);
        assertThat(recruiterFeed.at("/data/0/likeCount").asLong()).isEqualTo(1);
        assertThat(recruiterFeed.at("/data/0/commentCount").asLong()).isEqualTo(1);
        assertThat(recruiterFeed.at("/data/0/likedByCurrentUser").asBoolean()).isTrue();
        assertThat(candidateFeed.at("/data/0/likedByCurrentUser").asBoolean()).isFalse();
    }

    @Test
    void authorProjectionAllowsNullsAndNeverExposesPrivateFields() throws Exception {
        Account recruiter = recruiter("privacy-recruiter");
        jdbcTemplate.update("update users set avatar_url = null where id = ?", recruiter.userId());
        jdbcTemplate.update("delete from company_members where user_id = ?", recruiter.userId());
        insertPost(UUID.randomUUID().toString(), recruiter.userId(), "Public only", "2026-08-10T04:00:00Z");

        String json = mockMvc.perform(get("/api/v1/community/posts").header("Authorization", bearer(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].author.avatarUrl").isEmpty())
                .andExpect(jsonPath("$.data[0].author.companyName").isEmpty())
                .andReturn().getResponse().getContentAsString();
        for (String privateField : new String[]{"email", "passwordHash", "refreshToken", "accessToken",
                "resume", "verificationStatus", "acceptedTermsVersion"}) {
            assertThat(json).doesNotContain(privateField);
        }
        assertThat(objectMapper.readTree(json).at("/data/0/author").size()).isEqualTo(5);
    }

    private JsonNode create(Account account, String body) throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of("body", body));
        return read(mockMvc.perform(post("/api/v1/community/posts").header("Authorization", bearer(account))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode feed(Account account, Integer page, Integer pageSize) throws Exception {
        var request = get("/api/v1/community/posts").header("Authorization", bearer(account));
        if (page != null) request.queryParam("page", page.toString());
        if (pageSize != null) request.queryParam("pageSize", pageSize.toString());
        return read(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String insertPost(String id, String authorId, String body, String createdAt) {
        Timestamp time = Timestamp.from(Instant.parse(createdAt));
        jdbcTemplate.update("insert into community_posts (id,author_id,body,created_at,updated_at) values (?,?,?,?,?)",
                id, authorId, body, time, time);
        return id;
    }

    private Account recruiter(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String companyName = prefix + " Company";
        String body = """
                {"role":"RECRUITER","companyName":"%s","fullName":"Recruiter One",
                 "email":"%s","password":"StrongPass123!","acceptedTermsVersion":"2026-08"}
                """.formatted(companyName, email);
        JsonNode response = register(body);
        return new Account(response.at("/data/accessToken").asText(), response.at("/data/user/userId").asText(),
                companyName);
    }

    private Account candidate(String prefix) throws Exception {
        String body = """
                {"role":"CANDIDATE","fullName":"Candidate One","email":"%s",
                 "password":"StrongPass123!","acceptedTermsVersion":"2026-08"}
                """.formatted(uniqueEmail(prefix));
        JsonNode response = register(body);
        return new Account(response.at("/data/accessToken").asText(), response.at("/data/user/userId").asText(), null);
    }

    private JsonNode register(String body) throws Exception {
        return read(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode read(String body) throws Exception { return objectMapper.readTree(body); }
    private static String bearer(Account account) { return "Bearer " + account.accessToken(); }
    private static String uniqueEmail(String prefix) { return prefix + "-" + UUID.randomUUID() + "@example.com"; }
    private record Account(String accessToken, String userId, String companyName) {}
}
