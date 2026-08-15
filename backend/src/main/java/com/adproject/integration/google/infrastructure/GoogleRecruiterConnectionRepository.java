package com.adproject.integration.google.infrastructure;

import com.adproject.integration.google.domain.GoogleConnectionStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleRecruiterConnectionRepository extends JpaRepository<GoogleRecruiterConnectionEntity, String> {
    Optional<GoogleRecruiterConnectionEntity> findByRecruiterId(String recruiterId);
    boolean existsByRecruiterIdAndStatus(String recruiterId, GoogleConnectionStatus status);
    void deleteByRecruiterId(String recruiterId);
}
