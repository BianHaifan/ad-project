package com.adproject.application.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, String> {
    boolean existsByCandidateIdAndJobId(String candidateId, String jobId);
    Optional<ApplicationEntity> findByIdAndCandidateId(String id, String candidateId);
}
