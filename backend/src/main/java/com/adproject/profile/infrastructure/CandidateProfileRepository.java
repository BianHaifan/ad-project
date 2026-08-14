package com.adproject.profile.infrastructure;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface CandidateProfileRepository extends JpaRepository<CandidateProfileEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from CandidateProfileEntity profile where profile.userId = :userId")
    Optional<CandidateProfileEntity> findByUserIdForUpdate(@Param("userId") String userId);
}
