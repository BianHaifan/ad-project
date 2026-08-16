package com.adproject.recommendation.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.WorkplaceType;
import com.adproject.recommendation.api.RecommendationDtos.JobPreference;
import com.adproject.recommendation.api.RecommendationDtos.JobPreferenceResponse;
import com.adproject.recommendation.api.RecommendationDtos.SaveJobPreferenceRequest;
import com.adproject.recommendation.infrastructure.CandidateJobPreferenceEntity;
import com.adproject.recommendation.infrastructure.CandidateJobPreferenceRepository;
import com.adproject.user.domain.UserRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateJobPreferenceService {
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<List<WorkplaceType>> WORKPLACES = new TypeReference<>() {};
    private static final TypeReference<List<EmploymentType>> EMPLOYMENTS = new TypeReference<>() {};

    private final CandidateJobPreferenceRepository preferences;
    private final ObjectMapper mapper;
    private final Clock clock;

    public CandidateJobPreferenceService(
            CandidateJobPreferenceRepository preferences, ObjectMapper mapper, Clock clock) {
        this.preferences = preferences;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public JobPreferenceResponse get(AuthenticatedUser principal) {
        requireCandidate(principal);
        return new JobPreferenceResponse(preferences.findById(principal.userId())
                .map(this::toDto)
                .orElse(new JobPreference(
                        List.of(), List.of(), List.of(), List.of(), null,
                        com.adproject.job.domain.SalaryCurrency.SGD,
                        com.adproject.job.domain.SalaryPeriod.MONTH,
                        0, null, null)));
    }

    @Transactional
    public JobPreferenceResponse save(
            AuthenticatedUser principal, SaveJobPreferenceRequest request) {
        requireCandidate(principal);
        CandidateJobPreferenceEntity entity = preferences
                .findByCandidateIdForUpdate(principal.userId()).orElse(null);
        var now = clock.instant();
        if (entity == null) {
            if (request.expectedVersion() != 0) throw versionConflict();
            entity = preferences.save(new CandidateJobPreferenceEntity(
                    principal.userId(), write(request.desiredTitles()),
                    write(request.preferredLocations()), write(request.workplaceTypes()),
                    write(request.employmentTypes()), request.minimumSalary(),
                    request.salaryCurrency(), request.salaryPeriod(), 1, now, now));
        } else {
            if (entity.getVersion() != request.expectedVersion()) throw versionConflict();
            entity.replace(write(request.desiredTitles()), write(request.preferredLocations()),
                    write(request.workplaceTypes()), write(request.employmentTypes()),
                    request.minimumSalary(), request.salaryCurrency(), request.salaryPeriod(), now);
        }
        preferences.flush();
        return new JobPreferenceResponse(toDto(entity));
    }

    private JobPreference toDto(CandidateJobPreferenceEntity entity) {
        return new JobPreference(
                read(entity.getDesiredTitlesJson(), STRINGS),
                read(entity.getPreferredLocationsJson(), STRINGS),
                read(entity.getWorkplaceTypesJson(), WORKPLACES),
                read(entity.getEmploymentTypesJson(), EMPLOYMENTS),
                entity.getMinimumSalary(), entity.getSalaryCurrency(), entity.getSalaryPeriod(),
                entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to store candidate job preferences", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored candidate job preferences are invalid", exception);
        }
    }

    static void requireCandidate(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.CANDIDATE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }

    private static ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "The candidate job preferences have changed");
    }
}
