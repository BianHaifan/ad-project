package com.adproject.community.infrastructure;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.JpaRepository;
public interface CommunityDirectMessageRepository extends JpaRepository<CommunityDirectMessageEntity,String>{
 Page<CommunityDirectMessageEntity> findByConversationId(String conversationId,Pageable pageable);
}
