package com.adproject.community.api;
import java.time.Instant; import java.util.List;
public final class CommunityDirectDtos { private CommunityDirectDtos(){}
 public record Conversation(String conversationId,CommunityDtos.CommunityAuthor participant,Instant createdAt,Instant updatedAt){}
 public record ConversationResponse(Conversation data){}
 public record Message(String messageId,String conversationId,String senderId,String body,Instant sentAt){}
 public record MessageResponse(Message data){}
 public record MessageListResponse(List<Message> data,CommunityDtos.PageMeta meta){}
 public record SendMessageRequest(String body){}
}
