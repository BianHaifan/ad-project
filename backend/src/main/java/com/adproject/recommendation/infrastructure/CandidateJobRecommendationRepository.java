package com.adproject.recommendation.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidateJobRecommendationRepository
        extends JpaRepository<CandidateJobRecommendationEntity, String> {
    Optional<CandidateJobRecommendationEntity> findByCandidateIdAndJobId(
            String candidateId, String jobId);

    List<CandidateJobRecommendationEntity> findByCandidateIdAndJobIdIn(
            String candidateId, Collection<String> jobIds);

    @Modifying
    @Query("delete from CandidateJobRecommendationEntity recommendation "
            + "where recommendation.candidateId = :candidateId")
    int deleteAllForCandidate(@Param("candidateId") String candidateId);
}
