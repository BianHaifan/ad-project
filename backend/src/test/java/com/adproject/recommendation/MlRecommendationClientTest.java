package com.adproject.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.adproject.recommendation.application.MlRecommendationClient;
import com.adproject.recommendation.application.MlRecommendationClient.MlCandidate;
import com.adproject.recommendation.application.MlRecommendationClient.MlJob;
import com.adproject.recommendation.application.MlRecommendationClient.MlPreferences;
import com.adproject.recommendation.application.MlRecommendationClient.MlSalary;
import com.adproject.recommendation.application.RecommendationProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MlRecommendationClientTest {
    @Test
    void sendsSnakeCasePrivateContractAndParsesModelExplanation() {
        RecommendationProperties properties = new RecommendationProperties();
        properties.setBaseUrl("http://ml.test");
        properties.setInternalToken("private-token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MlRecommendationClient client = new MlRecommendationClient(
                builder.baseUrl(properties.getBaseUrl())
                        .defaultHeader("X-Internal-Token", properties.getInternalToken()).build(),
                properties);
        server.expect(once(), requestTo("http://ml.test/internal/v1/recommend/jobs"))
                .andExpect(header("X-Internal-Token", "private-token"))
                .andExpect(jsonPath("$.candidate.entity_id").value("candidate-1"))
                .andExpect(jsonPath("$.candidate.preferences.desired_titles[0]")
                        .value("Backend Engineer"))
                .andExpect(jsonPath("$.jobs[0].workplace_type").value("HYBRID"))
                .andRespond(withSuccess("""
                        {
                          "model_version": "match-hybrid-lsa-cf-v1",
                          "feature_version": "pair-features-v1+lsa64+cf32",
                          "generated_at": "2026-08-12T08:00:00Z",
                          "inference_ms": 5,
                          "hybrid": {
                            "enabled": true,
                            "components": ["RANKER", "LSA_EMBEDDING", "COLLABORATIVE_SVD"],
                            "weights": {"ranker": 0.85, "embedding": 0.10, "collaborative": 0.05},
                            "embedding_algorithm": "TFIDF_TRUNCATED_SVD_LSA",
                            "collaborative_algorithm": "IMPLICIT_FEEDBACK_TRUNCATED_SVD",
                            "collaborative_feedback_source": "SYNTHETIC_IMPLICIT_FEEDBACK"
                          },
                          "items": [{
                            "entity_id": "job-1",
                            "score": 98,
                            "rank": 1,
                            "strong_matches": ["Skills matched: 4 of 4"],
                            "gaps": [],
                            "evidence": ["python"],
                            "component_scores": {
                              "ranker": 98.0,
                              "embedding_cosine": 0.91,
                              "collaborative_latent": 0.72,
                              "hybrid_final": 98.0
                            },
                            "component_modes": {
                              "candidate_cf": "EMBEDDING_BRIDGED",
                              "job_cf": "EMBEDDING_BRIDGED"
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.recommendJobs(
                new MlCandidate("candidate-1", "Python backend work", "Backend Engineer",
                        List.of("Python"), 4.0,
                        new MlPreferences(List.of("Backend Engineer"), List.of("Singapore"),
                                List.of("HYBRID"), List.of("FULL_TIME"), null)),
                List.of(new MlJob("job-1", "Python Backend Engineer", "Build APIs",
                        List.of("3 years"), List.of("Python"), "Singapore", "HYBRID",
                        "FULL_TIME", new MlSalary(6000L, 9000L, "SGD", "MONTH"), 3.0)),
                1);

        assertThat(response.modelVersion()).isEqualTo("match-hybrid-lsa-cf-v1");
        assertThat(response.hybrid().enabled()).isTrue();
        assertThat(response.hybrid().components())
                .containsExactly("RANKER", "LSA_EMBEDDING", "COLLABORATIVE_SVD");
        assertThat(response.hybrid().weights()).containsEntry("collaborative", 0.05);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.entityId()).isEqualTo("job-1");
            assertThat(item.score()).isEqualTo(98);
            assertThat(item.evidence()).containsExactly("python");
            assertThat(item.componentScores()).containsEntry("embedding_cosine", 0.91);
            assertThat(item.componentModes())
                    .containsEntry("candidate_cf", "EMBEDDING_BRIDGED");
        });
        server.verify();
    }

    @Test
    void sendsReverseCandidatesRequestAndParsesRankedApplicants() {
        RecommendationProperties properties = new RecommendationProperties();
        properties.setBaseUrl("http://ml.test");
        properties.setInternalToken("private-token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MlRecommendationClient client = new MlRecommendationClient(
                builder.baseUrl(properties.getBaseUrl())
                        .defaultHeader("X-Internal-Token", properties.getInternalToken()).build(),
                properties);
        server.expect(once(), requestTo("http://ml.test/internal/v1/recommend/candidates"))
                .andExpect(header("X-Internal-Token", "private-token"))
                .andExpect(jsonPath("$.job.entity_id").value("job-1"))
                .andExpect(jsonPath("$.job.title").value("Cobol Engineer"))
                .andExpect(jsonPath("$.candidates[0].entity_id").value("candidate-1"))
                .andExpect(jsonPath("$.candidates[0].skills[0]").value("Cobol"))
                .andExpect(jsonPath("$.limit").value(2))
                .andRespond(withSuccess("""
                        {
                          "model_version": "match-hgb-v1",
                          "feature_version": "pair-features-v1",
                          "generated_at": "2026-08-12T08:00:00Z",
                          "inference_ms": 7,
                          "items": [{
                            "entity_id": "candidate-1",
                            "score": 91,
                            "rank": 1,
                            "strong_matches": ["Skills matched: 1 of 1"],
                            "gaps": [],
                            "evidence": ["cobol"]
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.recommendCandidates(
                new MlJob("job-1", "Cobol Engineer", "Build systems", List.of(),
                        List.of("Cobol"), "Singapore", "HYBRID", "FULL_TIME",
                        new MlSalary(6000L, 9000L, "SGD", "MONTH"), null),
                List.of(new MlCandidate("candidate-1", "Cobol systems", "Engineer",
                        List.of("Cobol"), null, null)),
                2);

        assertThat(response.modelVersion()).isEqualTo("match-hgb-v1");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.entityId()).isEqualTo("candidate-1");
            assertThat(item.score()).isEqualTo(91);
            assertThat(item.evidence()).containsExactly("cobol");
        });
        server.verify();
    }
}
