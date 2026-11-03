package com.visionmapping.repository;

import com.visionmapping.entity.AiMessage;
import com.visionmapping.entity.enums.AiMessageRole;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {

    List<AiMessage> findByConversation_IdOrderByIdAsc(Long conversationId);

    List<AiMessage> findByUser_IdOrderByIdAsc(Long userId);

    long countByUser_IdAndRoleAndCreatedAtGreaterThanEqual(Long userId, AiMessageRole role, Instant from);
}
