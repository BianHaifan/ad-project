package com.adproject.dashboard.application;

import com.adproject.application.api.RecruiterApplicationDtos;
import com.adproject.application.application.RecruiterApplicationService;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.dashboard.api.DashboardResponses;
import com.adproject.job.application.JobService;
import com.adproject.user.domain.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private static final int RECENT_LIMIT = 3;

    private final CompanyMemberRepository members;
    private final CompanyRepository companies;
    private final RecruiterApplicationService recruiterApplications;
    private final JobService jobs;

    public DashboardService(CompanyMemberRepository members, CompanyRepository companies,
                            RecruiterApplicationService recruiterApplications, JobService jobs) {
        this.members = members;
        this.companies = companies;
        this.recruiterApplications = recruiterApplications;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public DashboardResponses.DashboardResponse dashboard(AuthenticatedUser principal) {
        String companyId = requireCompany(principal);
        CompanyEntity company = companies.findById(companyId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission"));
        RecruiterApplicationDtos.Counts counts = recruiterApplications.counts(companyId);
        var metrics = new DashboardResponses.Metrics(
                jobs.activeJobCount(companyId),
                counts.applied(), counts.inReview(), counts.interview(),
                company.getVerificationStatus().name());
        var data = new DashboardResponses.DashboardData(
                metrics,
                recruiterApplications.recentSummaries(companyId, RECENT_LIMIT),
                jobs.recentJobs(companyId, RECENT_LIMIT));
        return new DashboardResponses.DashboardResponse(data);
    }

    private String requireCompany(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.RECRUITER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
        return members.findByUserId(principal.userId()).map(member -> member.getCompanyId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission"));
    }
}
