package com.visionmapping.repository;

import com.visionmapping.entity.AiSafetyAlert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSafetyAlertRepository extends JpaRepository<AiSafetyAlert, Long> {

    List<AiSafetyAlert> findByCoach_IdOrderByCreatedAtDesc(Long coachId);

    List<AiSafetyAlert> findByCoachIsNullOrderByCreatedAtDesc();
}
