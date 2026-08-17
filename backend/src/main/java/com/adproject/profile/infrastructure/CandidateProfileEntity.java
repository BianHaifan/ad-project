package com.adproject.profile.infrastructure;

import com.adproject.profile.domain.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "candidate_profiles")
public class CandidateProfileEntity {
    @Id @Column(name = "user_id", length = 36, columnDefinition = "char(36)") private String userId;
    @Column(nullable = false, length = 200) private String headline;
    @Column(nullable = false, length = 100) private String location;
    @Column private Integer age;
    @Enumerated(EnumType.STRING) @Column(length = 32) private Gender gender;
    @Column(length = 32) private String phone;
    @Column(length = 100) private String birthplace;
    @Column(nullable = false) private int version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected CandidateProfileEntity() {}
    public CandidateProfileEntity(String userId, String headline, String location, Integer age, Gender gender, String phone, String birthplace, int version, Instant createdAt, Instant updatedAt) {
        this.userId = userId; this.headline = headline; this.location = location; this.age = age;
        this.gender = gender; this.phone = phone; this.birthplace = birthplace; this.version = version;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
    }
    public String getUserId() { return userId; }
    public String getHeadline() { return headline; }
    public String getLocation() { return location; }
    public Integer getAge() { return age; }
    public Gender getGender() { return gender; }
    public String getPhone() { return phone; }
    public String getBirthplace() { return birthplace; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void update(String headline, String location, Integer age, Gender gender, String phone, String birthplace, Instant now) {
        this.headline = headline; this.location = location; this.age = age;
        this.gender = gender; this.phone = phone; this.birthplace = birthplace; this.version++; this.updatedAt = now;
    }
}
