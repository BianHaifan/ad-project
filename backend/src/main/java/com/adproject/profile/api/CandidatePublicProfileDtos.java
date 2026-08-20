package com.adproject.profile.api;

import java.util.List;

public final class CandidatePublicProfileDtos {
    private CandidatePublicProfileDtos() {}

    /** Public company summary exposed to candidates. Deliberately smaller than the recruiter-side company DTO. */
    public record CompanySummary(String companyId, String name, String logoUrl, String verificationStatus) {}

    /**
     * Public recruiter profile projection. Contains only fields a candidate may see and never the recruiter's
     * email, registration time, account status, or role.
     */
    public record RecruiterPublicProfile(String recruiterId, String fullName, String avatarUrl, String title,
                                         String bio, CompanySummary company) {}

    public record RecruiterPublicProfileResponse(RecruiterPublicProfile data) {}

    /** Public company projection. It exposes public company facts and published openings, never audit metadata. */
    public record CompanyPublicProfile(String companyId, String name, String logoUrl, String description,
                                       String location, String verificationStatus, String stage,
                                       String employeeRange, String website, long activeJobCount,
                                       List<CompanyOpenJob> openJobs) {}

    /** A compact public job projection used only inside the company profile. */
    public record CompanyOpenJob(String jobId, String title, String location, String employmentType,
                                 String workplaceType) {}

    public record CompanyPublicProfileResponse(CompanyPublicProfile data) {}
}
