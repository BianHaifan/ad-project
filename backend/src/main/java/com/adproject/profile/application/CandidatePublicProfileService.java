package com.adproject.profile.application;

import com.adproject.application.infrastructure.ApplicationRepository;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.conversation.infrastructure.ConversationRepository;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.Visibility;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.profile.api.CandidatePublicProfileDtos.CompanyPublicProfile;
import com.adproject.profile.api.CandidatePublicProfileDtos.CompanyPublicProfileResponse;
import com.adproject.profile.api.CandidatePublicProfileDtos.CompanyOpenJob;
import com.adproject.profile.api.CandidatePublicProfileDtos.CompanySummary;
import com.adproject.profile.api.CandidatePublicProfileDtos.RecruiterPublicProfile;
import com.adproject.profile.api.CandidatePublicProfileDtos.RecruiterPublicProfileResponse;
import com.adproject.profile.infrastructure.RecruiterProfileEntity;
import com.adproject.profile.infrastructure.RecruiterProfileRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate-facing public projections for recruiters and companies. Both endpoints require the CANDIDATE role and
 * only resolve when the caller can legitimately reach the target — via an application, a conversation, or a public
 * job the recruiter/company has published (so a candidate browsing a job can view its recruiter and company before
 * applying). Missing resources and resources the candidate cannot reach both return the same 404.
 */
@Service
public class CandidatePublicProfileService {
    private final UserRepository users;
    private final RecruiterProfileRepository profiles;
    private final CompanyRepository companies;
    private final CompanyMemberRepository members;
    private final ApplicationRepository applications;
    private final ConversationRepository conversations;
    private final JobRepository jobs;

    public CandidatePublicProfileService(UserRepository users, RecruiterProfileRepository profiles,
                                         CompanyRepository companies, CompanyMemberRepository members,
                                         ApplicationRepository applications, ConversationRepository conversations,
                                         JobRepository jobs) {
        this.users = users;
        this.profiles = profiles;
        this.companies = companies;
        this.members = members;
        this.applications = applications;
        this.conversations = conversations;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public RecruiterPublicProfileResponse getRecruiter(AuthenticatedUser principal, String recruiterId) {
        requireCandidate(principal);
        UserEntity recruiter = users.findById(recruiterId)
                .filter(user -> user.getRole() == UserRole.RECRUITER)
                .orElseThrow(CandidatePublicProfileService::notFound);
        if (!canSeeRecruiter(principal.userId(), recruiterId)) {
            throw notFound();
        }
        CompanySummary company = requireCompany(recruiter);
        RecruiterProfileEntity profile = profiles.findById(recruiterId).orElse(null);
        return new RecruiterPublicProfileResponse(new RecruiterPublicProfile(
                recruiter.getId(),
                recruiter.getFullName(),
                recruiter.getAvatarUrl(),
                profile == null ? "" : profile.getTitle(),
                profile == null ? null : profile.getBio(),
                company));
    }

    @Transactional(readOnly = true)
    public CompanyPublicProfileResponse getCompany(AuthenticatedUser principal, String companyId) {
        requireCandidate(principal);
        CompanyEntity company = companies.findById(companyId).orElseThrow(CandidatePublicProfileService::notFound);
        if (!canSeeCompany(principal.userId(), companyId)) {
            throw notFound();
        }
        List<CompanyOpenJob> openJobs = jobs.findByCompanyIdAndStatusAndVisibility(companyId,
                        JobStatus.ACTIVE, Visibility.PUBLIC,
                        PageRequest.of(0, 3, Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"))))
                .stream()
                .map(job -> new CompanyOpenJob(job.getId(), job.getTitle(), job.getLocation(),
                        job.getEmploymentType().name(), job.getWorkplaceType().name()))
                .toList();
        return new CompanyPublicProfileResponse(new CompanyPublicProfile(
                company.getId(),
                company.getName(),
                company.getLogoUrl(),
                company.getDescription(),
                company.getLocation(),
                company.getVerificationStatus() == null ? null : company.getVerificationStatus().name(),
                company.getStage(),
                company.getEmployeeRange(),
                company.getWebsite(),
                jobs.countByCompanyIdAndStatusAndVisibility(companyId, JobStatus.ACTIVE, Visibility.PUBLIC),
                openJobs));
    }

    private boolean canSeeRecruiter(String candidateId, String recruiterId) {
        return applications.existsByCandidateIdAndRecruiterId(candidateId, recruiterId)
                || conversations.existsByCandidateIdAndRecruiterId(candidateId, recruiterId)
                || jobs.existsByRecruiterIdAndStatusAndVisibility(recruiterId, JobStatus.ACTIVE, Visibility.PUBLIC);
    }

    private boolean canSeeCompany(String candidateId, String companyId) {
        return applications.existsByCandidateIdAndCompanyId(candidateId, companyId)
                || conversations.existsByCandidateIdAndCompanyId(candidateId, companyId)
                || jobs.existsByCompanyIdAndStatusAndVisibility(companyId, JobStatus.ACTIVE, Visibility.PUBLIC);
    }

    private CompanySummary requireCompany(UserEntity recruiter) {
        CompanyMemberEntity member = members.findByUserId(recruiter.getId())
                .orElseThrow(CandidatePublicProfileService::notFound);
        CompanyEntity company = companies.findById(member.getCompanyId())
                .orElseThrow(CandidatePublicProfileService::notFound);
        return new CompanySummary(
                company.getId(),
                company.getName(),
                company.getLogoUrl(),
                company.getVerificationStatus() == null ? null : company.getVerificationStatus().name());
    }

    private static void requireCandidate(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.CANDIDATE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }
}
