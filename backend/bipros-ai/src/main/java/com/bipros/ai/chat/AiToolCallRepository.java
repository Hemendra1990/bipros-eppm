package com.bipros.ai.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiToolCallRepository extends JpaRepository<AiToolCall, UUID> {
    List<AiToolCall> findByMessageId(UUID messageId);
}
