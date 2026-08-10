package com.adproject.resume.domain;

public record ResumeExperience(
        String experienceId,
        String title,
        String company,
        String description,
        String startDate,
        String endDate
) {}
