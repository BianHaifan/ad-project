package com.adproject.conversation.infrastructure;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String>,
        JpaSpecificationExecutor<ConversationEntity> {
    Optional<ConversationEntity> findByApplicationId(String applicationId);

    Page<ConversationEntity> findByCandidateId(String candidateId, Pageable pageable);
}
