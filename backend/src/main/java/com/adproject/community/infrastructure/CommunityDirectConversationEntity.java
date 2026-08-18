package com.adproject.community.infrastructure;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="community_direct_conversations")
public class CommunityDirectConversationEntity {
 @Id @Column(length=36,columnDefinition="char(36)") private String id;
 @Column(name="participant_a_id",nullable=false,length=36,columnDefinition="char(36)") private String participantAId;
 @Column(name="participant_b_id",nullable=false,length=36,columnDefinition="char(36)") private String participantBId;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected CommunityDirectConversationEntity(){}
 public CommunityDirectConversationEntity(String id,String a,String b,Instant now){this.id=id;participantAId=a;participantBId=b;createdAt=now;updatedAt=now;}
 public String getId(){return id;} public String getParticipantAId(){return participantAId;} public String getParticipantBId(){return participantBId;}
 public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public void touch(Instant now){updatedAt=now;}
 public boolean includes(String userId){return participantAId.equals(userId)||participantBId.equals(userId);}
 public String other(String userId){return participantAId.equals(userId)?participantBId:participantAId;}
}
