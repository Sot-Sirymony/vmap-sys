package com.visionmapping.repository;

import java.time.LocalDate;
import com.visionmapping.entity.enums.WorkStatus;
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

    /** FR-67: work due on the date or already overdue, with the dream it serves. */
    @Query("""
            select i from VisionStep i join fetch i.goal g join fetch g.dream
            where i.user.id = :userId and i.archived = false
              and i.targetDate <= :date and i.status not in :excluded
            """)
    List<VisionStep> findDueForDailyPlan(@Param("userId") Long userId, @Param("date") LocalDate date, @Param("excluded") List<WorkStatus> excluded);

    boolean existsByUser_IdAndArchivedFalseAndTargetDateIsNotNull(Long userId);

    /** FR-75: steps planned for a window of days. */
    List<VisionStep> findByUser_IdAndArchivedFalseAndTargetDateBetween(Long userId, LocalDate from, LocalDate to);

    /** FR-77: the last time the user touched a step. */
    @Query("select max(s.updatedAt) from VisionStep s where s.user.id = :userId")
    java.time.Instant findLastUpdatedAt(@Param("userId") Long userId);
}
