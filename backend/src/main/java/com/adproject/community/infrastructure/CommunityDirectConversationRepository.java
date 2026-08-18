package com.adproject.community.infrastructure;
import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface CommunityDirectConversationRepository extends JpaRepository<CommunityDirectConversationEntity,String>{
 Optional<CommunityDirectConversationEntity> findByParticipantAIdAndParticipantBId(String a,String b);
}
