package com.adproject.conversation.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachmentEntity, String> {
    Optional<MessageAttachmentEntity> findByMessageId(String messageId);

    List<MessageAttachmentEntity> findByMessageIdIn(List<String> messageIds);
}
