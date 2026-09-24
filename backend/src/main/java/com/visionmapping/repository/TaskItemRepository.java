package com.visionmapping.repository;

import com.visionmapping.entity.TaskItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskItemRepository extends JpaRepository<TaskItem, Long>, CodedRepository<TaskItem> {

    List<TaskItem> findByStep_IdAndUser_Id(Long stepId, Long userId);

    List<TaskItem> findByStep_IdAndUser_IdAndArchivedFalse(Long stepId, Long userId);

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
}
