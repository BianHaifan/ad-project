package com.adproject.application.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, String> {
    Optional<IdempotencyRecordEntity> findByUserIdAndOperationAndIdempotencyKey(
            String userId, String operation, String idempotencyKey);
}
