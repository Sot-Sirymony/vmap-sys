package com.visionmapping.repository;

import java.time.LocalDate;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.entity.TaskItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskItemRepository extends JpaRepository<TaskItem, Long>, CodedRepository<TaskItem> {

    List<TaskItem> findByStep_IdAndUser_Id(Long stepId, Long userId);

    List<TaskItem> findByStep_IdAndUser_IdAndArchivedFalse(Long stepId, Long userId);

    /** Every task the user still works on, for the Tasks page's overall order. */
    List<TaskItem> findByUser_IdAndArchivedFalse(Long userId);

    /**
     * The three numbers a step's progress roll-up needs, without loading the
     * tasks. JPQL rather than a native query on purpose: Hibernate flushes
     * pending changes before a JPQL query, so a roll-up that runs straight
     * after a task edit still sees that edit.
     */
    @Query("""
            select new com.visionmapping.repository.ProgressRollup(
                count(t), coalesce(sum(t.progressPercent), 0),
                count(case when t.status = com.visionmapping.entity.enums.WorkStatus.COMPLETED then 1 end))
            from TaskItem t
            where t.step.id = :stepId and t.user.id = :userId and t.archived = false
            """)
    ProgressRollup rollUpForStep(@Param("stepId") Long stepId, @Param("userId") Long userId);

    /** Next sort position in a step: a count, not a load of every sibling. */
    long countByStep_IdAndUser_IdAndArchivedFalse(Long stepId, Long userId);

    /** FR-67: work due on the date or already overdue, with the dream it serves. */
    @Query("""
            select i from TaskItem i join fetch i.step s join fetch s.goal g join fetch g.dream
            where i.user.id = :userId and i.archived = false
              and i.dueDate <= :date and i.status not in :excluded
            """)
    List<TaskItem> findDueForDailyPlan(@Param("userId") Long userId, @Param("date") LocalDate date, @Param("excluded") List<WorkStatus> excluded);

    /** FR-77: the last time the user touched a task. */
    @Query("select max(t.updatedAt) from TaskItem t where t.user.id = :userId")
    java.time.Instant findLastUpdatedAt(@Param("userId") Long userId);
}
