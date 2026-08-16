package com.adproject.recommendation.application;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MlRecommendationClient {
    private final RestClient restClient;
    private final RecommendationProperties properties;

    @Autowired
    public MlRecommendationClient(RestClient.Builder builder, RecommendationProperties properties) {
        this(createRestClient(builder, properties), properties);
    }

    public MlRecommendationClient(RestClient restClient, RecommendationProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private static RestClient createRestClient(
            RestClient.Builder builder, RecommendationProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-Internal-Token", properties.getInternalToken())
                .requestFactory(requestFactory)
                .build();
    }

    public MlResponse recommendJobs(MlCandidate candidate, List<MlJob> jobs, int limit) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("ML recommendation service is disabled");
        }
        MlResponse response = restClient.post()
                .uri("/internal/v1/recommend/jobs")
                .body(new RecommendJobsRequest(candidate, jobs, limit))
                .retrieve()
                .body(MlResponse.class);
        if (response == null || response.items() == null) {
            throw new IllegalStateException("ML recommendation service returned an empty response");
        }
        return response;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RecommendJobsRequest(MlCandidate candidate, List<MlJob> jobs, int limit) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MlCandidate(
            String entityId,
            String resumeText,
            String headline,
            List<String> skills,
            Double yearsExperience,
            MlPreferences preferences) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MlPreferences(
            List<String> desiredTitles,
            List<String> preferredLocations,
            List<String> workplaceTypes,
            List<String> employmentTypes,
            MlSalary minimumSalary) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MlJob(
            String entityId,
            String title,
            String description,
            List<String> requirements,
            List<String> skills,
            String location,
            String workplaceType,
            String employmentType,
            MlSalary salary,
            Double requiredYearsExperience) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MlSalary(Long minimum, Long maximum, String currency, String period) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MlResponse(
            String modelVersion,
            String featureVersion,
            Instant generatedAt,
            int inferenceMs,
            List<MlItem> items) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MlItem(
            String entityId,
            int score,
            int rank,
            List<String> strongMatches,
            List<String> gaps,
            List<String> evidence) {}
}
