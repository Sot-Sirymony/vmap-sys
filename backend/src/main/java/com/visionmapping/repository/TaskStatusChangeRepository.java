package com.visionmapping.repository;

import com.visionmapping.entity.TaskStatusChange;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** V27 SR-FR-06: task status history. */
public interface TaskStatusChangeRepository extends JpaRepository<TaskStatusChange, Long> {

    List<TaskStatusChange> findByTask_IdAndUser_IdOrderByChangedAtAscIdAsc(Long taskId, Long userId);

    /** The history of several tasks at once, oldest first, for waiting and blocked time. */
    @Query("select c from TaskStatusChange c where c.user.id = :userId and c.task.id in :taskIds order by c.changedAt asc, c.id asc")
    List<TaskStatusChange> findForTasks(@Param("userId") Long userId, @Param("taskIds") Collection<Long> taskIds);
}
