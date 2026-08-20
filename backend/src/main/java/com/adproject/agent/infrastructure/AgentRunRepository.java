package com.adproject.agent.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRunRepository extends JpaRepository<AgentRunEntity, String> {
    Optional<AgentRunEntity> findByIdAndUserId(String id, String userId);
    Optional<AgentRunEntity> findByUserIdAndExecutionIdempotencyKey(String userId, String executionIdempotencyKey);
    Optional<AgentRunEntity> findFirstByUserIdOrderByUpdatedAtDesc(String userId);
    boolean existsByConversationIdAndUserId(String conversationId, String userId);
    List<AgentRunEntity> findByConversationIdAndUserId(String conversationId, String userId, Pageable pageable);

    @Query("""
            select run from AgentRunEntity run
            where run.userId = :userId
              and run.id = (
                select max(latest.id) from AgentRunEntity latest
                where latest.userId = :userId
                  and latest.conversationId = run.conversationId
                  and latest.updatedAt = (
                    select max(newest.updatedAt) from AgentRunEntity newest
                    where newest.userId = :userId
                      and newest.conversationId = run.conversationId
                  )
              )
            order by run.updatedAt desc, run.id desc
            """)
    List<AgentRunEntity> findLatestRunPerConversation(@Param("userId") String userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from AgentRunEntity run where run.id = :id and run.userId = :userId")
    Optional<AgentRunEntity> findOwnedForUpdate(@Param("id") String id, @Param("userId") String userId);

    @Modifying
    @Query("delete from AgentRunEntity run where run.userId = :userId and run.conversationId = :conversationId")
    void deleteAllByUserIdAndConversationId(@Param("userId") String userId, @Param("conversationId") String conversationId);
}
