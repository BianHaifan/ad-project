package com.adproject.application.infrastructure;

import com.adproject.application.domain.ApplicationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, String> {
    boolean existsByJobIdAndCandidateId(String jobId, String candidateId);

    @Query("select application.status from ApplicationEntity application " +
            "where application.jobId = :jobId and application.candidateId = :candidateId")
    Optional<ApplicationStatus> findStatus(@Param("jobId") String jobId, @Param("candidateId") String candidateId);
}
