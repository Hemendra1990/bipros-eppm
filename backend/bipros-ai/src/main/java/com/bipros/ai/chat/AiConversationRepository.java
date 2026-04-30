package com.bipros.ai.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {
    List<AiConversation> findByUserIdAndDeletedAtIsNullOrderByLastMessageAtDesc(UUID userId);
    List<AiConversation> findByUserIdAndProjectIdAndDeletedAtIsNullOrderByLastMessageAtDesc(UUID userId, UUID projectId);
}
