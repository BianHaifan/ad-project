package com.adproject.application.infrastructure;

import com.adproject.application.domain.ApplicationStatus;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, String>, JpaSpecificationExecutor<ApplicationEntity> {
    boolean existsByJobIdAndCandidateId(String jobId, String candidateId);

    Optional<ApplicationEntity> findByJobIdAndCandidateId(String jobId, String candidateId);

    @Query("select application.status from ApplicationEntity application " +
            "where application.jobId = :jobId and application.candidateId = :candidateId")
    Optional<ApplicationStatus> findStatus(@Param("jobId") String jobId, @Param("candidateId") String candidateId);

    List<ApplicationEntity> findByJobIdAndStatusInOrderByAppliedAtAscIdAsc(String jobId,
                                                                           Collection<ApplicationStatus> statuses);

    Page<ApplicationEntity> findByCandidateId(String candidateId, Pageable pageable);
    Page<ApplicationEntity> findByCandidateIdAndStatusIn(String candidateId, Collection<ApplicationStatus> statuses,
                                                          Pageable pageable);
    long countByCandidateIdAndStatusIn(String candidateId, Collection<ApplicationStatus> statuses);
    Optional<ApplicationEntity> findByIdAndCandidateId(String id, String candidateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from ApplicationEntity application " +
            "where application.id = :applicationId and application.candidateId = :candidateId")
    Optional<ApplicationEntity> findOwnByIdForUpdate(@Param("applicationId") String applicationId,
                                                      @Param("candidateId") String candidateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from ApplicationEntity application where application.id = :applicationId")
    Optional<ApplicationEntity> findByIdForUpdate(@Param("applicationId") String applicationId);

    @Query("select count(application) from ApplicationEntity application, JobEntity job " +
            "where application.jobId = job.id and job.companyId = :companyId and application.status = :status")
    long countByCompanyIdAndStatus(@Param("companyId") String companyId,
                                   @Param("status") ApplicationStatus status);

    @Query("select count(application) > 0 from ApplicationEntity application, JobEntity job " +
            "where application.jobId = job.id and application.candidateId = :candidateId " +
            "and job.companyId = :companyId")
    boolean existsByCandidateIdAndCompanyId(@Param("candidateId") String candidateId,
                                            @Param("companyId") String companyId);

    @Query("select count(application) > 0 from ApplicationEntity application, JobEntity job " +
            "where application.jobId = job.id and application.candidateId = :candidateId " +
            "and (job.ownerId = :recruiterId or (job.ownerId is null and job.createdBy = :recruiterId))")
    boolean existsByCandidateIdAndRecruiterId(@Param("candidateId") String candidateId,
                                              @Param("recruiterId") String recruiterId);
}
