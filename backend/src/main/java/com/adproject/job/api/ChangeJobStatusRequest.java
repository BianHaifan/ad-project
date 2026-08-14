package com.adproject.job.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeJobStatusRequest(
        @NotNull TargetStatus status,
        @NotBlank @Size(max = 500) String reason,
        @NotNull @Min(1) Integer expectedVersion
) {
    public enum TargetStatus {
        ACTIVE,
        PAUSED,
        CLOSED
    }
}
