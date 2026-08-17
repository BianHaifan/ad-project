package com.adproject.application.api;

import com.adproject.recommendation.api.RecommendationDtos.MatchAnalysis;
import com.adproject.recommendation.api.RecommendationDtos.RecommendationMeta;
import java.time.Instant;
import java.util.List;

/**
 * Read-only response DTOs for the recruiter AI-applicant-ranking endpoint. The candidate summary
 * intentionally omits email and the full resume: it only carries what is needed to render a ranked
 * card and navigate to the existing application detail page.
 */
public final class RecruiterApplicantRecommendationDtos {
    private RecruiterApplicantRecommendationDtos() {}

    public record ApplicantCandidateSummary(String candidateId, String fullName, String headline,
                                            String avatarUrl, String location) {}

    public record RecommendedApplicant(String applicationId, ApplicantCandidateSummary candidate,
                                       String status, Instant appliedAt, int matchScore, int rank,
                                       MatchAnalysis matchAnalysis) {}

    public record RecommendedApplicantResponse(List<RecommendedApplicant> data, RecommendationMeta meta) {}
}
