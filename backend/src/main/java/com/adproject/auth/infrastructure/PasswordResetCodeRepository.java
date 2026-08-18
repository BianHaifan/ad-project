package com.adproject.auth.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCodeEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetCodeEntity> findFirstByUserIdOrderByCreatedAtDesc(String userId);
}
