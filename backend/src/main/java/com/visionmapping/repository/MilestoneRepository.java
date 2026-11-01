package com.visionmapping.repository;

import com.visionmapping.entity.Milestone;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findByDream_IdAndUser_IdAndArchivedFalseOrderByAchievedOnDesc(Long dreamId, Long userId);

    long countByUser_IdAndArchivedFalse(Long userId);

    List<Milestone> findByUser_Id(Long userId);
}
