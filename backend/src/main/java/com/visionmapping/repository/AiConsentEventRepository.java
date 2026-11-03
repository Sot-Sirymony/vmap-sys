package com.visionmapping.repository;

import com.visionmapping.entity.AiConsentEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConsentEventRepository extends JpaRepository<AiConsentEvent, Long> {

    List<AiConsentEvent> findByUser_IdOrderByCreatedAtAsc(Long userId);
}
