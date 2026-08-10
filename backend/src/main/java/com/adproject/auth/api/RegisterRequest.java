package com.adproject.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotNull String role,
        @Size(min = 1, max = 200) String companyName,
        @NotNull @Size(min = 1, max = 100) String fullName,
        @NotNull @Email String email,
        @NotNull @Size(min = 8, max = 128) String password,
        @NotNull String acceptedTermsVersion
) {}
