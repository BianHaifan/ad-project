package com.adproject.agent.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentStepRepository extends JpaRepository<AgentStepEntity, String> {
    List<AgentStepEntity> findByRunIdOrderBySequenceNoAsc(String runId);
}
