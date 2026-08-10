package com.adproject.company.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyMemberRepository extends JpaRepository<CompanyMemberEntity, String> {
    Optional<CompanyMemberEntity> findByUserId(String userId);
}
