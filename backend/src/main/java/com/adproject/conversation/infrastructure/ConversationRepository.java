package com.adproject.conversation.infrastructure;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String>,
        JpaSpecificationExecutor<ConversationEntity> {
    Optional<ConversationEntity> findByApplicationId(String applicationId);

    Page<ConversationEntity> findByCandidateId(String candidateId, Pageable pageable);

    boolean existsByCandidateIdAndCompanyId(String candidateId, String companyId);

    @Query("select count(conversation) > 0 from ConversationEntity conversation, JobEntity job " +
            "where conversation.jobId = job.id and conversation.candidateId = :candidateId " +
            "and (job.ownerId = :recruiterId or (job.ownerId is null and job.createdBy = :recruiterId))")
    boolean existsByCandidateIdAndRecruiterId(@Param("candidateId") String candidateId,
                                              @Param("recruiterId") String recruiterId);
}
