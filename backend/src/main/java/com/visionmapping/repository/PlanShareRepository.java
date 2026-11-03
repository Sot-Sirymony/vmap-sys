package com.visionmapping.repository;

import com.visionmapping.entity.PlanShare;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanShareRepository extends JpaRepository<PlanShare, Long> {

    List<PlanShare> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<PlanShare> findByUser_IdAndDream_IdOrderByCreatedAtDesc(Long userId, Long dreamId);

    Optional<PlanShare> findByTokenHash(String tokenHash);
}
