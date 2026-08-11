package com.adproject.application.application;

import com.adproject.application.infrastructure.ApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateApplicationStateService {
    private final ApplicationRepository repository;
    public CandidateApplicationStateService(ApplicationRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public String state(String candidateId, String jobId) {
        return repository.findStatus(jobId, candidateId).map(Enum::name).orElse("NOT_APPLIED");
    }
}
