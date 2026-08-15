package com.adproject.application.application;

import com.adproject.application.api.ApplicationDtos;
import com.adproject.application.api.InterviewDtos;
import com.adproject.application.domain.InterviewStatus;
import com.adproject.application.infrastructure.ApplicationEntity;
import com.adproject.application.infrastructure.ApplicationStatusEventEntity;
import com.adproject.application.infrastructure.InterviewEntity;
import com.adproject.application.infrastructure.ResumeSnapshotEntity;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.resume.api.ResumeDtos;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CandidateApplicationResponseMapper {
    private static final TypeReference<List<ResumeDtos.Experience>> EXPERIENCES = new TypeReference<>() {};
    private final ObjectMapper mapper;

    public CandidateApplicationResponseMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ApplicationDtos.CandidateApplicationSummary summary(ApplicationEntity application, JobEntity job,
                                                                 CompanyEntity company,
                                                                 List<ApplicationStatusEventEntity> events,
                                                                 InterviewEntity interview) {
        return new ApplicationDtos.CandidateApplicationSummary(
                application.getId(), application.getJobId(), application.getStatus().name(),
                application.getAppliedAt(), application.getUpdatedAt(), application.getVersion(), job.getTitle(),
                company(company), null, scheduledAt(interview), timeline(events));
    }

    public ApplicationDtos.CandidateApplicationDetail detail(ApplicationEntity application,
                                                              ResumeSnapshotEntity snapshot, JobEntity job,
                                                              CompanyEntity company,
                                                              List<ApplicationStatusEventEntity> events,
                                                              InterviewEntity interview) {
        var snapshotDto = resumeSnapshot(snapshot);
        return new ApplicationDtos.CandidateApplicationDetail(
                application.getId(), application.getJobId(), application.getStatus().name(),
                application.getAppliedAt(), application.getUpdatedAt(), application.getVersion(), job.getTitle(),
                company(company), null, scheduledAt(interview), timeline(events), snapshotDto,
                interview == null ? null : interviewDto(interview), nextSteps(application, company));
    }

    private java.time.Instant scheduledAt(InterviewEntity interview) {
        return interview != null && interview.getStatus() == InterviewStatus.SCHEDULED
                ? interview.getScheduledAt() : null;
    }

    private InterviewDtos.Interview interviewDto(InterviewEntity interview) {
        return new InterviewDtos.Interview(interview.getId(), interview.getApplicationId(),
                interview.getScheduledAt(), interview.getTimezone(), interview.getDurationMinutes(),
                interview.getMode().name(), interview.getLocationOrMeetingUrl(), null,
                interview.getStatus().name(), interview.getVersion(), interview.getCreatedAt(),
                interview.getUpdatedAt(), interview.getMeetingProvider().name(),
                interview.getMeetingSyncStatus().name());
    }

    public ApplicationDtos.ResumeSnapshot resumeSnapshot(ResumeSnapshotEntity snapshot) {
        List<ApplicationDtos.Experience> experiences = readExperiences(snapshot.getExperiencesJson()).stream()
                .map(value -> new ApplicationDtos.Experience(value.experienceId(), value.title(), value.company(),
                        value.description(), value.startDate(), value.endDate())).toList();
        return new ApplicationDtos.ResumeSnapshot(snapshot.getId(), snapshot.getCapturedAt(),
                snapshot.getResumeId(), snapshot.getFullName(), snapshot.getAge(), snapshot.getLocation(),
                snapshot.getHeadline(), snapshot.getSummary(), experiences, snapshot.getResumeVersion(),
                snapshot.getResumeCreatedAt(), snapshot.getResumeUpdatedAt());
    }

    private ApplicationDtos.Company company(CompanyEntity company) {
        return new ApplicationDtos.Company(company.getId(), company.getName(), company.getLogoUrl(),
                company.getStage(), company.getEmployeeRange(), company.getVerificationStatus().name(),
                company.getWebsite(), company.getDescription(), company.getLocation(), company.getVersion(),
                company.getCreatedAt(), company.getUpdatedAt());
    }

    private List<ApplicationDtos.TimelineStep> timeline(List<ApplicationStatusEventEntity> events) {
        return events.stream().map(event -> new ApplicationDtos.TimelineStep(
                event.getToStatus().name(), true, event.getOccurredAt())).toList();
    }

    private List<ApplicationDtos.NextStep> nextSteps(ApplicationEntity application, CompanyEntity company) {
        if (application.getStatus() == com.adproject.application.domain.ApplicationStatus.REJECTED
                || application.getStatus() == com.adproject.application.domain.ApplicationStatus.WITHDRAWN) {
            return List.of();
        }
        return List.of(
                new ApplicationDtos.NextStep("RECRUITER_REVIEW", "Recruiter review",
                        company.getName() + " will review your resume snapshot."),
                new ApplicationDtos.NextStep("STATUS_UPDATE", "Status update",
                        "Your application status will update after recruiter review."),
                new ApplicationDtos.NextStep("INTERVIEW_INVITATION", "Interview invitation",
                        "You will be notified if an interview is scheduled."));
    }

    private List<ResumeDtos.Experience> readExperiences(String json) {
        try {
            return mapper.readValue(json, EXPERIENCES);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored resume experiences are invalid", exception);
        }
    }
}
