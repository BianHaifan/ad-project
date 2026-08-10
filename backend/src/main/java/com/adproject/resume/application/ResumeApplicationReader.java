package com.adproject.resume.application;

import com.adproject.common.api.ApiException;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ResumeApplicationReader {
    private final ResumeRepository resumeRepository;

    public ResumeApplicationReader(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    public ResumeForApplication requireOwnedResume(String candidateId, String resumeId) {
        ResumeEntity resume = resumeRepository.findByIdAndCandidateId(resumeId, candidateId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESUME_NOT_FOUND", "Resume not found"));
        return new ResumeForApplication(resume.getId(), resume.getFullName(), resume.getAge(), resume.getLocation(),
                resume.getHeadline(), resume.getSummary(), resume.getExperiences(), resume.getVersion(),
                resume.getCreatedAt(), resume.getUpdatedAt());
    }
}
