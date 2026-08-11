package com.adproject.job.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<JobEntity, String>, JpaSpecificationExecutor<JobEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from JobEntity job where job.id = :jobId and job.companyId = :companyId")
    Optional<JobEntity> findOwnJobForUpdate(@Param("jobId") String jobId, @Param("companyId") String companyId);
}
