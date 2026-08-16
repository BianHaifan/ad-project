package com.adproject.profile.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.profile.api.RecruiterProfileDtos.CompanySummary;
import com.adproject.profile.api.RecruiterProfileDtos.ProfileResponse;
import com.adproject.profile.api.RecruiterProfileDtos.RecruiterProfileData;
import com.adproject.profile.api.RecruiterProfileDtos.UpdateRecruiterProfileRequest;
import com.adproject.profile.infrastructure.RecruiterProfileEntity;
import com.adproject.profile.infrastructure.RecruiterProfileRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecruiterProfileService {
    private static final int TITLE_MAX = 100;
    private static final int BIO_MAX = 1000;
    private static final int AVATAR_URL_MAX = 500;

    private final UserRepository userRepository;
    private final RecruiterProfileRepository profileRepository;
    private final CompanyMemberRepository memberRepository;
    private final CompanyRepository companyRepository;
    private final Clock clock;

    public RecruiterProfileService(UserRepository userRepository,
                                   RecruiterProfileRepository profileRepository,
                                   CompanyMemberRepository memberRepository,
                                   CompanyRepository companyRepository,
                                   Clock clock) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.memberRepository = memberRepository;
        this.companyRepository = companyRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(AuthenticatedUser principal) {
        requireRecruiter(principal);
        UserEntity user = userRepository.findById(principal.userId()).orElseThrow(RecruiterProfileService::notFound);
        CompanySummary company = requireCompany(user);
        RecruiterProfileEntity profile = profileRepository.findById(user.getId()).orElse(null);
        return response(user, company, profile);
    }

    @Transactional
    public ProfileResponse update(AuthenticatedUser principal, UpdateRecruiterProfileRequest request) {
        requireRecruiter(principal);
        if (request == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Request validation failed", Map.of("request", "must not be null"));
        }
        UserEntity user = userRepository.findById(principal.userId()).orElseThrow(RecruiterProfileService::notFound);
        CompanySummary company = requireCompany(user);
        RecruiterProfileEntity profile = profileRepository.findById(user.getId()).orElse(null);

        Map<String, String> fieldErrors = validate(request, profile == null);
        if (!fieldErrors.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Request validation failed", fieldErrors);
        }

        if (profile != null && !request.isFullNamePresent() && !request.isTitlePresent()
                && !request.isBioPresent() && !request.isAvatarUrlPresent()) {
            return response(user, company, profile);
        }

        Instant now = DatabaseTimePrecision.micros(clock.instant());
        if (request.isFullNamePresent()) {
            user.updateFullName(request.getFullName().trim(), now);
        }
        if (request.isAvatarUrlPresent()) {
            user.updateAvatarUrl(normalizeAvatarUrl(request.getAvatarUrl()), now);
        }

        if (profile == null) {
            profile = new RecruiterProfileEntity(
                    user.getId(),
                    request.getTitle().trim(),
                    normalizeBio(request.getBio()),
                    now,
                    now);
        } else {
            String title = request.isTitlePresent() ? request.getTitle().trim() : profile.getTitle();
            String bio = request.isBioPresent() ? normalizeBio(request.getBio()) : profile.getBio();
            profile.update(title, bio, now);
        }

        profileRepository.saveAndFlush(profile);
        return response(user, company, profile);
    }

    private ProfileResponse response(UserEntity user, CompanySummary company, RecruiterProfileEntity profile) {
        String title = profile == null ? "" : profile.getTitle();
        String bio = profile == null ? null : profile.getBio();
        return new ProfileResponse(new RecruiterProfileData(
                user.getId(),
                user.getFullName(),
                user.getAvatarUrl(),
                title,
                bio,
                company,
                user.getEmail(),
                user.getCreatedAt(),
                profile == null ? user.getUpdatedAt() : profile.getUpdatedAt()));
    }

    private CompanySummary requireCompany(UserEntity user) {
        CompanyMemberEntity member = memberRepository.findByUserId(user.getId())
                .orElseThrow(RecruiterProfileService::notFound);
        CompanyEntity company = companyRepository.findById(member.getCompanyId())
                .orElseThrow(RecruiterProfileService::notFound);
        return new CompanySummary(
                company.getId(),
                company.getName(),
                company.getLogoUrl(),
                company.getVerificationStatus() == null ? null : company.getVerificationStatus().name());
    }

    private static Map<String, String> validate(UpdateRecruiterProfileRequest request, boolean missingProfile) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request.isFullNamePresent()) {
            String value = request.getFullName();
            if (value == null || value.isBlank()) {
                errors.put("fullName", "must not be blank");
            } else if (value.trim().length() > 100) {
                errors.put("fullName", "must not exceed 100 characters");
            }
        }
        if (request.isTitlePresent()) {
            String value = request.getTitle();
            if (value == null || value.isBlank()) {
                errors.put("title", "must not be blank");
            } else if (value.trim().length() > TITLE_MAX) {
                errors.put("title", "must not exceed 100 characters");
            }
        } else if (missingProfile) {
            errors.put("title", "must be provided when creating the recruiter profile");
        }
        if (request.isBioPresent() && request.getBio() != null && request.getBio().length() > BIO_MAX) {
            errors.put("bio", "must not exceed 1000 characters");
        }
        if (request.isAvatarUrlPresent() && request.getAvatarUrl() != null
                && request.getAvatarUrl().length() > AVATAR_URL_MAX) {
            errors.put("avatarUrl", "must not exceed 500 characters");
        }
        return errors;
    }

    private static String normalizeBio(String bio) {
        if (bio == null || bio.isBlank()) {
            return null;
        }
        return bio.trim();
    }

    private static String normalizeAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }
        return avatarUrl.trim();
    }

    private static void requireRecruiter(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.RECRUITER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }
}
