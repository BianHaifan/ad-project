package com.adproject.recommendation.application;

import com.adproject.job.infrastructure.JobEntity;
import com.adproject.recommendation.api.RecommendationDtos.MatchAnalysis;
import com.adproject.recommendation.infrastructure.CandidateJobRecommendationEntity;
import com.adproject.recommendation.infrastructure.CandidateJobRecommendationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationSnapshotService {
    private final CandidateJobRecommendationRepository recommendations;
    private final ObjectMapper mapper;

    public RecommendationSnapshotService(
            CandidateJobRecommendationRepository recommendations, ObjectMapper mapper) {
        this.recommendations = recommendations;
        this.mapper = mapper;
    }

    @Transactional
    public void save(
            String candidateId,
            int resumeVersion,
            int preferenceVersion,
            String source,
            String modelVersion,
            String featureVersion,
            Instant generatedAt,
            List<SnapshotInput> values) {
        // A recommendation run is a complete snapshot. Removing the previous run prevents
        // a job that dropped out of the new Top-N from retaining an apparently current score.
        recommendations.deleteAllForCandidate(candidateId);
        for (SnapshotInput value : values) {
            CandidateJobRecommendationEntity entity = new CandidateJobRecommendationEntity(
                    UUID.randomUUID().toString(), candidateId, value.job().getId(),
                    value.score(), source, modelVersion, featureVersion,
                    write(value.analysis().strongMatches()), write(value.analysis().gaps()),
                    write(value.analysis().evidence()), resumeVersion, preferenceVersion,
                    value.job().getVersion(), generatedAt);
            recommendations.save(entity);
        }
        recommendations.flush();
    }

    private String write(List<String> value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to store recommendation explanation", exception);
        }
    }

    public record SnapshotInput(JobEntity job, int score, MatchAnalysis analysis) {}
}
