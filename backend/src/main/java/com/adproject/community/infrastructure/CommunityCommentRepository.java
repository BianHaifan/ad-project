package com.adproject.community.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityCommentRepository extends JpaRepository<CommunityCommentEntity, String> {
    Page<CommunityCommentEntity> findByPostId(String postId, Pageable pageable);
    long countByPostId(String postId);
}
