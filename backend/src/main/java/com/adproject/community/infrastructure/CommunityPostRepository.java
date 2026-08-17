package com.adproject.community.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostRepository extends JpaRepository<CommunityPostEntity, String> {}
