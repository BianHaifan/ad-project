package com.adproject.application.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ApplicationStatusEventRepository extends JpaRepository<ApplicationStatusEventEntity, String> {}
