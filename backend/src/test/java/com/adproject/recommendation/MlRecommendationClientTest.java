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
                          "model_version": "match-hgb-v1",
                          "feature_version": "pair-features-v1",
                          "generated_at": "2026-08-12T08:00:00Z",
                          "inference_ms": 5,
                          "items": [{
                            "entity_id": "job-1",
                            "score": 98,
                            "rank": 1,
                            "strong_matches": ["Skills matched: 4 of 4"],
                            "gaps": [],
                            "evidence": ["python"]
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

        assertThat(response.modelVersion()).isEqualTo("match-hgb-v1");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.entityId()).isEqualTo("job-1");
            assertThat(item.score()).isEqualTo(98);
            assertThat(item.evidence()).containsExactly("python");
        });
        server.verify();
    }
}
