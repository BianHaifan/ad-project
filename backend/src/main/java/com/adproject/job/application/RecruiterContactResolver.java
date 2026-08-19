package com.adproject.job.application;

import com.adproject.job.api.CandidateJobResponses.RecruiterContact;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.profile.infrastructure.RecruiterProfileEntity;
import com.adproject.profile.infrastructure.RecruiterProfileRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class RecruiterContactResolver {
    private final UserRepository users;
    private final RecruiterProfileRepository recruiterProfiles;

    public RecruiterContactResolver(UserRepository users, RecruiterProfileRepository recruiterProfiles) {
        this.users = users;
        this.recruiterProfiles = recruiterProfiles;
    }

    public RecruiterContact resolve(JobEntity job) {
        String recruiterId = job.getOwnerId() != null ? job.getOwnerId() : job.getCreatedBy();
        UserEntity recruiter = users.findById(recruiterId)
                .filter(user -> user.getRole() == UserRole.RECRUITER)
                .orElse(null);
        if (recruiter == null) {
            return null;
        }
        RecruiterProfileEntity profile = recruiterProfiles.findById(recruiterId).orElse(null);
        return new RecruiterContact(recruiter.getId(), recruiter.getFullName(),
                profile == null ? "" : profile.getTitle(), recruiter.getAvatarUrl());
    }
}
