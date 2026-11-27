package com.visionmapping.repository;

import com.visionmapping.entity.TaskStatusChange;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** V27 SR-FR-06: task status history. */
public interface TaskStatusChangeRepository extends JpaRepository<TaskStatusChange, Long> {

    List<TaskStatusChange> findByTask_IdAndUser_IdOrderByChangedAtAscIdAsc(Long taskId, Long userId);

    /** V33 (D-33-9): one page of a task's status changes. */
    Page<TaskStatusChange> findByTask_IdAndUser_Id(Long taskId, Long userId, Pageable pageable);

    /** The history of several tasks at once, oldest first, for waiting and blocked time. */
    @Query("select c from TaskStatusChange c where c.user.id = :userId and c.task.id in :taskIds order by c.changedAt asc, c.id asc")
    List<TaskStatusChange> findForTasks(@Param("userId") Long userId, @Param("taskIds") Collection<Long> taskIds);
}
