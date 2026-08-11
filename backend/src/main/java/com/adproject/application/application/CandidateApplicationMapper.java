package com.adproject.application.application;

import com.adproject.application.api.ApplicationResponses.CandidateApplicationDetail;
import com.adproject.application.api.ApplicationResponses.Company;
import com.adproject.application.api.ApplicationResponses.Experience;
import com.adproject.application.api.ApplicationResponses.NextStep;
import com.adproject.application.api.ApplicationResponses.ResumeSnapshot;
import com.adproject.application.api.ApplicationResponses.SubmitApplicationResponse;
import com.adproject.application.api.ApplicationResponses.TimelineStep;
import com.adproject.application.domain.ApplicationStatus;
import com.adproject.application.infrastructure.ApplicationEntity;
import com.adproject.application.infrastructure.ApplicationStatusEventEntity;
import com.adproject.application.infrastructure.ResumeSnapshotEntity;
import com.adproject.company.application.CompanyView;
import com.adproject.job.application.JobForApplication;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CandidateApplicationMapper {
    private static final List<ApplicationStatus> CANDIDATE_TIMELINE = List.of(
            ApplicationStatus.APPLIED, ApplicationStatus.IN_REVIEW, ApplicationStatus.INTERVIEW);

    public SubmitApplicationResponse toResponse(ApplicationEntity application, ResumeSnapshotEntity snapshot,
                                                JobForApplication job,
                                                List<ApplicationStatusEventEntity> events) {
        var detail = new CandidateApplicationDetail(application.getId(), application.getJobId(),
                application.getStatus(), application.getAppliedAt(), application.getUpdatedAt(),
                application.getVersion(), job.title(), toCompany(job.company()), null, null,
                toTimeline(events), toSnapshot(snapshot), null, nextSteps(job.company().name()));
        return new SubmitApplicationResponse(detail);
    }

    private List<TimelineStep> toTimeline(List<ApplicationStatusEventEntity> events) {
        return CANDIDATE_TIMELINE.stream().map(status -> {
            Instant occurredAt = events.stream()
                    .filter(event -> event.getToStatus() == status)
                    .map(ApplicationStatusEventEntity::getOccurredAt)
                    .findFirst()
                    .orElse(null);
            return new TimelineStep(status, occurredAt != null, occurredAt);
        }).toList();
    }

    private ResumeSnapshot toSnapshot(ResumeSnapshotEntity snapshot) {
        List<Experience> experiences = snapshot.getExperiences().stream()
                .map(value -> new Experience(value.experienceId(), value.title(), value.company(),
                        value.description(), value.startDate(), value.endDate()))
                .toList();
        return new ResumeSnapshot(snapshot.getSourceResumeId(), snapshot.getFullName(), snapshot.getAge(),
                snapshot.getLocation(), snapshot.getHeadline(), snapshot.getSummary(), experiences,
                snapshot.getResumeVersion(), snapshot.getResumeCreatedAt(), snapshot.getResumeUpdatedAt(),
                snapshot.getId(), snapshot.getCapturedAt());
    }

    private Company toCompany(CompanyView company) {
        return new Company(company.companyId(), company.name(), company.logoUrl(), company.stage(),
                company.employeeRange(), company.verificationStatus(), company.website(), company.description(),
                company.location(), company.version(), company.createdAt(), company.updatedAt());
    }

    private List<NextStep> nextSteps(String companyName) {
        return List.of(
                new NextStep("RECRUITER_REVIEW", "Recruiter review",
                        companyName + " reviews your resume snapshot."),
                new NextStep("STATUS_UPDATE", "Status update",
                        "You can track every application stage in My applications."),
                new NextStep("INTERVIEW_INVITATION", "Interview invitation",
                        "We will notify you if an interview is scheduled."));
    }
}
