package com.adproject.agent.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentStepRepository extends JpaRepository<AgentStepEntity, String> {
    List<AgentStepEntity> findByRunIdOrderBySequenceNoAsc(String runId);

    @Modifying
    @Query("delete from AgentStepEntity step where step.runId in " +
            "(select run.id from AgentRunEntity run where run.userId = :userId and run.conversationId = :conversationId)")
    void deleteAllByConversation(@Param("userId") String userId, @Param("conversationId") String conversationId);
}
