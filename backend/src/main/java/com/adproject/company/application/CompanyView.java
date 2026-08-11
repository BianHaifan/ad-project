package com.adproject.company.application;

import java.time.Instant;

public record CompanyView(
        String companyId,
        String name,
        String logoUrl,
        String stage,
        String employeeRange,
        String verificationStatus,
        String website,
        String description,
        String location,
        int version,
        Instant createdAt,
        Instant updatedAt
) {}
