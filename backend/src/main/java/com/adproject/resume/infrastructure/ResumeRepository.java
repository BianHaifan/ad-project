package com.adproject.resume.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<ResumeEntity, String> {
    Optional<ResumeEntity> findByIdAndCandidateId(String id, String candidateId);
}
