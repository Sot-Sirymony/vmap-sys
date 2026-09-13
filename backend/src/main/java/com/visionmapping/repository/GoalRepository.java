package com.visionmapping.repository;

import com.visionmapping.entity.Goal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long>, UserScopedRepository<Goal> {

    List<Goal> findByDream_IdAndUser_Id(Long dreamId, Long userId);

    List<Goal> findByDream_IdAndUser_IdAndArchivedFalse(Long dreamId, Long userId);
}
