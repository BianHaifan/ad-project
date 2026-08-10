package com.adproject.application.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from IdempotencyRecordEntity record where record.userId = :userId " +
            "and record.operationName = :operationName and record.idempotencyKey = :idempotencyKey")
    Optional<IdempotencyRecordEntity> findForUpdate(@Param("userId") String userId,
                                                    @Param("operationName") String operationName,
                                                    @Param("idempotencyKey") String idempotencyKey);
}
