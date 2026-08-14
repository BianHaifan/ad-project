package com.adproject.profile.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ProfileDtos {
    private ProfileDtos() {}
    public record CandidateStats(int chatCount, int applicationCount, int interviewCount, int savedJobCount) {}
    public record CandidateProfile(String userId, String fullName, String email, String headline, String avatarUrl,
                                   String location, CandidateStats stats, int version, Instant createdAt, Instant updatedAt) {}
    public record ProfileResponse(CandidateProfile data) {}
    public static final class UpdateProfileRequest {
        @Size(min=1,max=100) private String fullName; private boolean fullNamePresent;
        @Size(max=200) private String headline; private boolean headlinePresent;
        @Size(max=100) private String location; private boolean locationPresent;
        @NotNull @Min(1) private Integer expectedVersion;
        @JsonSetter("fullName") public void setFullName(String value) { fullName=value; fullNamePresent=true; }
        @JsonSetter("headline") public void setHeadline(String value) { headline=value; headlinePresent=true; }
        @JsonSetter("location") public void setLocation(String value) { location=value; locationPresent=true; }
        public void setExpectedVersion(Integer value) { expectedVersion=value; }
        public String getFullName(){return fullName;} public boolean isFullNamePresent(){return fullNamePresent;}
        public String getHeadline(){return headline;} public boolean isHeadlinePresent(){return headlinePresent;}
        public String getLocation(){return location;} public boolean isLocationPresent(){return locationPresent;}
        public Integer getExpectedVersion(){return expectedVersion;}
    }
}
