package com.adproject.conversation.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<MessageEntity, String> {
    Optional<MessageEntity> findBySenderIdAndIdempotencyKey(String senderId, String idempotencyKey);

    Optional<MessageEntity> findByConversationIdAndClientMessageId(String conversationId, String clientMessageId);

    @Query("select m from MessageEntity m where m.conversationId = :conversationId order by m.sentAt desc, m.id desc")
    List<MessageEntity> findLatest(@Param("conversationId") String conversationId, Pageable pageable);

    @Query("select m from MessageEntity m where m.conversationId = :conversationId " +
            "and (:beforeSentAt is null or m.sentAt < :beforeSentAt " +
            "or (m.sentAt = :beforeSentAt and m.id < :beforeId)) " +
            "order by m.sentAt desc, m.id desc")
    List<MessageEntity> pageBefore(@Param("conversationId") String conversationId,
                                   @Param("beforeSentAt") Instant beforeSentAt,
                                   @Param("beforeId") String beforeId,
                                   Pageable pageable);

    @Query("select count(m) from MessageEntity m where m.conversationId = :conversationId " +
            "and m.senderId <> :userId and (:lastReadAt is null or m.sentAt > :lastReadAt)")
    long countUnread(@Param("conversationId") String conversationId,
                     @Param("userId") String userId,
                     @Param("lastReadAt") Instant lastReadAt);
}
