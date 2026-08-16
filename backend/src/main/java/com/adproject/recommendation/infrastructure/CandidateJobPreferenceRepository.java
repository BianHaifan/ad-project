package com.adproject.recommendation.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidateJobPreferenceRepository
        extends JpaRepository<CandidateJobPreferenceEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select preference from CandidateJobPreferenceEntity preference where preference.candidateId = :candidateId")
    Optional<CandidateJobPreferenceEntity> findByCandidateIdForUpdate(
            @Param("candidateId") String candidateId);
}
