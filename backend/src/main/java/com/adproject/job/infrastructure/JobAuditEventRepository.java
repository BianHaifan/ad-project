package com.adproject.job.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobAuditEventRepository extends JpaRepository<JobAuditEventEntity, String> {
}
