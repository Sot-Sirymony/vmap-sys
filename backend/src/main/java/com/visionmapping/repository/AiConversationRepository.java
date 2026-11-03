package com.visionmapping.repository;

import com.visionmapping.entity.AiConversation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    List<AiConversation> findByUser_IdOrderByUpdatedAtDesc(Long userId);
}
