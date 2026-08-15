package com.adproject.application.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface InterviewRepository extends JpaRepository<InterviewEntity, String> {
    Optional<InterviewEntity> findByApplicationId(String applicationId);
    boolean existsByApplicationId(String applicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select interview from InterviewEntity interview where interview.id = :interviewId")
    Optional<InterviewEntity> findByIdForUpdate(@Param("interviewId") String interviewId);
}
