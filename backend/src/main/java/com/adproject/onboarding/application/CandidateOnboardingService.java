package com.adproject.onboarding.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.job.domain.SalaryCurrency;
import com.adproject.job.domain.SalaryPeriod;
import com.adproject.onboarding.api.CandidateOnboardingRequest;
import com.adproject.profile.infrastructure.CandidateProfileEntity;
import com.adproject.profile.infrastructure.CandidateProfileRepository;
import com.adproject.recommendation.infrastructure.CandidateJobPreferenceEntity;
import com.adproject.recommendation.infrastructure.CandidateJobPreferenceRepository;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateOnboardingService {
    private final UserRepository users;
    private final CandidateProfileRepository profiles;
    private final ResumeRepository resumes;
    private final CandidateJobPreferenceRepository preferences;
    private final ObjectMapper mapper;
    private final Clock clock;

    public CandidateOnboardingService(UserRepository users, CandidateProfileRepository profiles,
                                      ResumeRepository resumes, CandidateJobPreferenceRepository preferences,
                                      ObjectMapper mapper, Clock clock) {
        this.users = users; this.profiles = profiles; this.resumes = resumes;
        this.preferences = preferences; this.mapper = mapper; this.clock = clock;
    }

    @Transactional
    public void complete(AuthenticatedUser principal, CandidateOnboardingRequest request) {
        requireCandidate(principal);
        var user = users.findByIdForUpdate(principal.userId()).orElseThrow();
        var now = DatabaseTimePrecision.micros(clock.instant());

        var profile = profiles.findByUserIdForUpdate(user.getId()).orElse(null);
        if (profile == null) {
            profiles.save(new CandidateProfileEntity(user.getId(), request.headline().trim(),
                    request.location().trim(), request.age(), null, null, null, 1, now, now));
        } else {
            profile.update(request.headline().trim(), request.location().trim(), request.age(),
                    profile.getGender(), profile.getPhone(), profile.getBirthplace(), now);
        }

        String skills = json(request.skills().stream().map(String::trim).distinct().toList());
        String resumeSummary = request.resumeSummary() == null ? "" : request.resumeSummary().trim();
        var resume = resumes.findByCandidateIdForUpdate(user.getId()).orElse(null);
        if (resume == null) {
            resumes.save(new ResumeEntity(UUID.randomUUID().toString(), user.getId(), user.getFullName(),
                    request.age(), request.location().trim(), request.headline().trim(),
                    resumeSummary, "[]", skills, 1, now, now));
        } else {
            resume.replace(user.getFullName(), request.age(), request.location().trim(), request.headline().trim(),
                    resumeSummary, resume.getExperiencesJson(), skills, now);
        }

        String desiredTitles = json(List.of(request.desiredTitle().trim()));
        String locations = json(List.of(request.preferredLocation().trim()));
        String workplaces = json(List.of(request.workplaceType()));
        String employments = json(List.of(request.employmentType()));
        var preference = preferences.findByCandidateIdForUpdate(user.getId()).orElse(null);
        if (preference == null) {
            preferences.save(new CandidateJobPreferenceEntity(user.getId(), desiredTitles, locations,
                    workplaces, employments, null, SalaryCurrency.SGD, SalaryPeriod.MONTH, 1, now, now));
        } else {
            preference.replace(desiredTitles, locations, workplaces, employments, preference.getMinimumSalary(),
                    preference.getSalaryCurrency(), preference.getSalaryPeriod(), now);
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Unable to store onboarding data", exception); }
    }

    private static void requireCandidate(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.CANDIDATE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }
}
