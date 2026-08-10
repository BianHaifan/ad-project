package com.adproject.application.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitApplicationRequest(
        @NotBlank @Size(max = 36) String resumeId,
        @NotBlank @Email @Size(max = 255) String contactEmail,
        @NotNull Boolean shareProfile
) {}
