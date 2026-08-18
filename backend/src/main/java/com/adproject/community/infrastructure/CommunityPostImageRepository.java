package com.adproject.community.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostImageRepository extends JpaRepository<CommunityPostImageEntity, String> {
    List<CommunityPostImageEntity> findByPostIdInOrderByPostIdAscPositionAsc(List<String> postIds);
    List<CommunityPostImageEntity> findByPostIdOrderByPositionAsc(String postId);
}
