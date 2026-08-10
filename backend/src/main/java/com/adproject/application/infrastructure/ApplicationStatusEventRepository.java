package com.adproject.application.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationStatusEventRepository extends JpaRepository<ApplicationStatusEventEntity, String> {
    List<ApplicationStatusEventEntity> findByApplicationIdOrderByOccurredAtAsc(String applicationId);
}
