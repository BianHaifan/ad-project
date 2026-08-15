package com.adproject.application.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewAuditEventRepository extends JpaRepository<InterviewAuditEventEntity, String> {
    List<InterviewAuditEventEntity> findByInterviewIdOrderByOccurredAtAscIdAsc(String interviewId);
}
