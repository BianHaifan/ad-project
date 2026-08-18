package com.adproject.community.infrastructure;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="community_direct_messages")
public class CommunityDirectMessageEntity {
 @Id @Column(length=36,columnDefinition="char(36)") private String id;
 @Column(name="conversation_id",nullable=false,length=36,columnDefinition="char(36)") private String conversationId;
 @Column(name="sender_id",nullable=false,length=36,columnDefinition="char(36)") private String senderId;
 @Column(nullable=false,columnDefinition="TEXT") private String body;
 @Column(name="sent_at",nullable=false) private Instant sentAt;
 protected CommunityDirectMessageEntity(){}
 public CommunityDirectMessageEntity(String id,String conversationId,String senderId,String body,Instant sentAt){this.id=id;this.conversationId=conversationId;this.senderId=senderId;this.body=body;this.sentAt=sentAt;}
 public String getId(){return id;} public String getConversationId(){return conversationId;} public String getSenderId(){return senderId;} public String getBody(){return body;} public Instant getSentAt(){return sentAt;}
}
