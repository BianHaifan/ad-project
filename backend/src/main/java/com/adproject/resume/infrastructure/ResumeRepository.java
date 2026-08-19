package com.adproject.resume.infrastructure;
import jakarta.persistence.LockModeType; import java.util.Optional; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface ResumeRepository extends JpaRepository<ResumeEntity,String>{
 boolean existsByCandidateId(String candidateId);
 Optional<ResumeEntity> findByCandidateId(String candidateId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select resume from ResumeEntity resume where resume.id = :resumeId")
 Optional<ResumeEntity> findByIdForUpdate(@Param("resumeId") String resumeId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select resume from ResumeEntity resume where resume.candidateId = :candidateId")
 Optional<ResumeEntity> findByCandidateIdForUpdate(@Param("candidateId") String candidateId);
}
