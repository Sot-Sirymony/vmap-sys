package com.visionmapping.repository;

import com.visionmapping.entity.AiConversation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    List<AiConversation> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    /** V11 (PB-FR-50): the plan assistant's conversations for one dream and section, newest first. */
    List<AiConversation> findByUser_IdAndDreamIdAndSectionKeyOrderByUpdatedAtDesc(Long userId, Long dreamId, String sectionKey);

    /** V15 (AH-FR-11): the page assistant's conversations for one page and item, newest first. */
    List<AiConversation> findByUser_IdAndDreamIdIsNullAndSectionKeyOrderByUpdatedAtDesc(Long userId, String sectionKey);
}
