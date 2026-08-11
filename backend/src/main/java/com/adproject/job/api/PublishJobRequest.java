package com.adproject.job.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PublishJobRequest(@NotNull @Min(1) Integer expectedVersion) {
}
