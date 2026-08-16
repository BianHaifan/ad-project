package com.adproject.profile.api;

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

    /**
     * Public company projection. Contains only the reliably available public fields and never internal audit data.
     */
    public record CompanyPublicProfile(String companyId, String name, String logoUrl, String description,
                                       String location, String verificationStatus) {}

    public record CompanyPublicProfileResponse(CompanyPublicProfile data) {}
}
