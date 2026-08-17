package com.adproject.job.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateSavedJobRepository
        extends JpaRepository<CandidateSavedJobEntity, String> {
    Optional<CandidateSavedJobEntity> findByCandidateIdAndJobId(String candidateId, String jobId);

    List<CandidateSavedJobEntity> findByCandidateIdAndJobIdIn(
            String candidateId, Collection<String> jobIds);

    List<CandidateSavedJobEntity> findByCandidateIdOrderByCreatedAtDesc(String candidateId);

    void deleteByCandidateIdAndJobId(String candidateId, String jobId);
}
