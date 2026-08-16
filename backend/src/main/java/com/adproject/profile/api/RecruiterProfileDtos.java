package com.adproject.profile.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.Instant;

public final class RecruiterProfileDtos {
    private RecruiterProfileDtos() {}

    public record CompanySummary(String companyId, String name, String logoUrl, String verificationStatus) {}

    public record RecruiterProfileData(String userId, String fullName, String avatarUrl, String title, String bio,
                                       CompanySummary company, String email, Instant createdAt, Instant updatedAt) {}

    public record ProfileResponse(RecruiterProfileData data) {}

    public static final class UpdateRecruiterProfileRequest {
        private String fullName;
        private boolean fullNamePresent;
        private String title;
        private boolean titlePresent;
        private String bio;
        private boolean bioPresent;
        private String avatarUrl;
        private boolean avatarUrlPresent;

        @JsonSetter("fullName")
        public void setFullName(String value) { this.fullName = value; this.fullNamePresent = true; }
        @JsonSetter("title")
        public void setTitle(String value) { this.title = value; this.titlePresent = true; }
        @JsonSetter("bio")
        public void setBio(String value) { this.bio = value; this.bioPresent = true; }
        @JsonSetter("avatarUrl")
        public void setAvatarUrl(String value) { this.avatarUrl = value; this.avatarUrlPresent = true; }

        public String getFullName() { return fullName; }
        public boolean isFullNamePresent() { return fullNamePresent; }
        public String getTitle() { return title; }
        public boolean isTitlePresent() { return titlePresent; }
        public String getBio() { return bio; }
        public boolean isBioPresent() { return bioPresent; }
        public String getAvatarUrl() { return avatarUrl; }
        public boolean isAvatarUrlPresent() { return avatarUrlPresent; }
    }
}
