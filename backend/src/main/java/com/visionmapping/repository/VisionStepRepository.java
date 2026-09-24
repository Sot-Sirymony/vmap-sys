package com.visionmapping.repository;

import com.visionmapping.entity.VisionStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisionStepRepository extends JpaRepository<VisionStep, Long>, CodedRepository<VisionStep> {

    List<VisionStep> findByGoal_IdAndUser_Id(Long goalId, Long userId);

    List<VisionStep> findByGoal_IdAndUser_IdAndArchivedFalse(Long goalId, Long userId);

    /** The goal-level counterpart of {@link TaskItemRepository#rollUpForStep}. */
    @Query("""
            select new com.visionmapping.repository.ProgressRollup(
                count(s), coalesce(sum(s.progressPercent), 0),
                count(case when s.status = com.visionmapping.entity.enums.WorkStatus.COMPLETED then 1 end))
            from VisionStep s
            where s.goal.id = :goalId and s.user.id = :userId and s.archived = false
            """)
    ProgressRollup rollUpForGoal(@Param("goalId") Long goalId, @Param("userId") Long userId);
}
