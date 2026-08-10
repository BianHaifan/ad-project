package com.adproject.user.application;

import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateSubmissionLock {
    private final UserRepository userRepository;

    public CandidateSubmissionLock(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String userId) {
        UserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated candidate is missing"));
        if (user.getRole() != UserRole.CANDIDATE) {
            throw new IllegalStateException("Submission lock requires a candidate account");
        }
    }
}
