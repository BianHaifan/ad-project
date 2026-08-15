package com.adproject.application.api;

import com.adproject.application.domain.InterviewMode;
import com.adproject.application.domain.InterviewStatus;
import com.adproject.application.domain.MeetingProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class InterviewDtos {
    private InterviewDtos() {}

    public record Interview(String interviewId, String applicationId, Instant scheduledAt, String timezone,
                            int durationMinutes, String mode, String locationOrMeetingUrl, String note,
                            String status, int version, Instant createdAt, Instant updatedAt,
                            String meetingProvider, String meetingSyncStatus) {}

    public record CreateInterviewRequest(
            @NotNull Instant scheduledAt,
            @NotBlank @Size(max = 64) String timezone,
            @NotNull @Min(1) @Max(1440) Integer durationMinutes,
            @NotNull InterviewMode mode,
            @Size(max = 1000) String locationOrMeetingUrl,
            @Size(max = 500) String note,
            MeetingProvider meetingProvider,
            @NotNull @Min(1) Integer expectedApplicationVersion
    ) {}

    public record UpdateInterviewRequest(
            Instant scheduledAt,
            @Size(max = 64) String timezone,
            @Min(1) @Max(1440) Integer durationMinutes,
            InterviewMode mode,
            @Size(max = 1000) String locationOrMeetingUrl,
            @Size(max = 500) String note,
            InterviewStatus status,
            @NotNull @Min(1) Integer expectedVersion
    ) {}

    public record InterviewResponse(Interview data) {}
}
