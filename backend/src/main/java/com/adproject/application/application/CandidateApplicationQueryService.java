package com.adproject.application.application;

import com.adproject.application.api.ApplicationDtos;
import com.adproject.application.domain.ApplicationListFilter;
import com.adproject.application.domain.ApplicationStatus;
import com.adproject.application.infrastructure.*;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.user.domain.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateApplicationQueryService {
    private static final List<ApplicationStatus> ACTIVE = List.of(ApplicationStatus.APPLIED, ApplicationStatus.IN_REVIEW);
    private static final List<ApplicationStatus> INTERVIEW = List.of(ApplicationStatus.INTERVIEW);
    private static final List<ApplicationStatus> ARCHIVED = List.of(ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN);

    private final ApplicationRepository applications;
    private final ApplicationStatusEventRepository events;
    private final ResumeSnapshotRepository snapshots;
    private final JobRepository jobs;
    private final CompanyRepository companies;
    private final CandidateApplicationResponseMapper mapper;
    private final Clock clock;

    public CandidateApplicationQueryService(ApplicationRepository applications,
                                            ApplicationStatusEventRepository events,
                                            ResumeSnapshotRepository snapshots, JobRepository jobs,
                                            CompanyRepository companies,
                                            CandidateApplicationResponseMapper mapper, Clock clock) {
        this.applications = applications;
        this.events = events;
        this.snapshots = snapshots;
        this.jobs = jobs;
        this.companies = companies;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ApplicationDtos.CandidateApplicationListResponse list(AuthenticatedUser principal,
                                                                  ApplicationListFilter filter,
                                                                  int page, int pageSize) {
        requireCandidate(principal);
        var pageable = PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Order.desc("appliedAt"), Sort.Order.desc("id")));
        Page<ApplicationEntity> result = filter == null
                ? applications.findByCandidateId(principal.userId(), pageable)
                : applications.findByCandidateIdAndStatusIn(principal.userId(), filter.statuses(), pageable);
        var data = result.getContent().stream().map(this::summary).toList();
        var counts = new ApplicationDtos.ApplicationCounts(
                applications.countByCandidateIdAndStatusIn(principal.userId(), ACTIVE),
                applications.countByCandidateIdAndStatusIn(principal.userId(), INTERVIEW),
                applications.countByCandidateIdAndStatusIn(principal.userId(), ARCHIVED));
        return new ApplicationDtos.CandidateApplicationListResponse(data,
                new ApplicationDtos.CandidateApplicationListMeta(page, pageSize, result.getTotalElements(),
                        result.hasNext(), counts));
    }

    @Transactional(readOnly = true)
    public ApplicationDtos.CandidateApplicationDetailResponse detail(AuthenticatedUser principal,
                                                                      String applicationId) {
        requireCandidate(principal);
        return new ApplicationDtos.CandidateApplicationDetailResponse(detail(
                applications.findByIdAndCandidateId(applicationId, principal.userId()).orElseThrow(this::notFound)));
    }

    @Transactional
    public ApplicationDtos.CandidateApplicationDetailResponse withdraw(AuthenticatedUser principal,
                                                                        String applicationId,
                                                                        ApplicationDtos.WithdrawApplicationRequest request,
                                                                        String requestId) {
        requireCandidate(principal);
        ApplicationEntity application = applications.findOwnByIdForUpdate(applicationId, principal.userId())
                .orElseThrow(this::notFound);
        if (application.getVersion() != request.expectedVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The application has changed");
        }
        if (application.getStatus() == ApplicationStatus.REJECTED
                || application.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_APPLICATION_TRANSITION",
                    "The application cannot be withdrawn from its current status");
        }
        ApplicationStatus before = application.getStatus();
        Instant now = clock.instant();
        var job = jobs.findById(application.getJobId()).orElseThrow(this::notFound);
        application.withdraw(now);
        events.save(new ApplicationStatusEventEntity(UUID.randomUUID().toString(), application.getId(),
                principal.userId(), job.getCompanyId(), before, ApplicationStatus.WITHDRAWN, now,
                request.reason().trim(), requestId));
        applications.flush();
        return new ApplicationDtos.CandidateApplicationDetailResponse(detail(application));
    }

    private ApplicationDtos.CandidateApplicationSummary summary(ApplicationEntity application) {
        var job = jobs.findById(application.getJobId()).orElseThrow(this::notFound);
        var company = companies.findById(job.getCompanyId()).orElseThrow(this::notFound);
        return mapper.summary(application, job, company,
                events.findByApplicationIdOrderByOccurredAtAscIdAsc(application.getId()));
    }

    private ApplicationDtos.CandidateApplicationDetail detail(ApplicationEntity application) {
        var job = jobs.findById(application.getJobId()).orElseThrow(this::notFound);
        var company = companies.findById(job.getCompanyId()).orElseThrow(this::notFound);
        var snapshot = snapshots.findById(application.getResumeSnapshotId()).orElseThrow(this::notFound);
        return mapper.detail(application, snapshot, job, company,
                events.findByApplicationIdOrderByOccurredAtAscIdAsc(application.getId()));
    }

    private static void requireCandidate(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.CANDIDATE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Application not found");
    }
}
