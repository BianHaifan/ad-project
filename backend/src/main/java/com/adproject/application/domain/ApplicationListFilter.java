package com.adproject.application.domain;

import java.util.List;

public enum ApplicationListFilter {
    ACTIVE(List.of(ApplicationStatus.APPLIED, ApplicationStatus.IN_REVIEW)),
    INTERVIEW(List.of(ApplicationStatus.INTERVIEW)),
    ARCHIVED(List.of(ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN));

    private final List<ApplicationStatus> statuses;

    ApplicationListFilter(List<ApplicationStatus> statuses) {
        this.statuses = statuses;
    }

    public List<ApplicationStatus> statuses() {
        return statuses;
    }
}
