package com.adproject.community.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CommunityPostRepository extends JpaRepository<CommunityPostEntity, String>, JpaSpecificationExecutor<CommunityPostEntity> {}
