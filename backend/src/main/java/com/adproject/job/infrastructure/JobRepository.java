package com.adproject.job.infrastructure;

import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.Visibility;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<JobEntity, String>, JpaSpecificationExecutor<JobEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from JobEntity job where job.id = :jobId")
    Optional<JobEntity> findByIdForUpdate(@Param("jobId") String jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from JobEntity job where job.id = :jobId and job.companyId = :companyId")
    Optional<JobEntity> findOwnJobForUpdate(@Param("jobId") String jobId, @Param("companyId") String companyId);

    long countByCompanyIdAndStatus(String companyId, JobStatus status);

    List<JobEntity> findByCompanyId(String companyId, Pageable pageable);

    boolean existsByCompanyIdAndStatusAndVisibility(String companyId, JobStatus status, Visibility visibility);

    @Query("select count(job) > 0 from JobEntity job " +
            "where (job.ownerId = :recruiterId or (job.ownerId is null and job.createdBy = :recruiterId)) " +
            "and job.status = :status and job.visibility = :visibility")
    boolean existsByRecruiterIdAndStatusAndVisibility(@Param("recruiterId") String recruiterId,
                                                      @Param("status") JobStatus status,
                                                      @Param("visibility") Visibility visibility);
}
