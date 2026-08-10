package com.adproject.resume.application;

import com.adproject.resume.domain.ResumeExperience;
import java.time.Instant;
import java.util.List;

public record ResumeForApplication(
        String resumeId,
        String fullName,
        int age,
        String location,
        String headline,
        String summary,
        List<ResumeExperience> experiences,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public ResumeForApplication {
        experiences = List.copyOf(experiences);
    }
}
