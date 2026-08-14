package com.adproject.dashboard.api;

import com.adproject.application.api.RecruiterApplicationDtos;
import com.adproject.job.api.JobResponses;
import java.util.List;

public final class DashboardResponses {
    private DashboardResponses() {}

    public record Metrics(long activeJobs, long appliedApplications, long inReviewApplications,
                          long interviewApplications, String companyVerificationStatus) {}

    public record DashboardData(Metrics metrics,
                                List<RecruiterApplicationDtos.Summary> recentApplications,
                                List<JobResponses.RecruiterJobDetail> recentJobs) {}

    public record DashboardResponse(DashboardData data) {}
}
