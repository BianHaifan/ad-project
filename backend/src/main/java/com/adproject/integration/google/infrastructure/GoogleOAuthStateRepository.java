package com.adproject.integration.google.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleOAuthStateRepository extends JpaRepository<GoogleOAuthStateEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from GoogleOAuthStateEntity state where state.stateHash = :stateHash")
    Optional<GoogleOAuthStateEntity> findByStateHashForUpdate(@Param("stateHash") String stateHash);

    void deleteByRecruiterId(String recruiterId);
}
