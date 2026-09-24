package com.visionmapping.repository;

import com.visionmapping.entity.TaskItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskItemRepository extends JpaRepository<TaskItem, Long>, CodedRepository<TaskItem> {

    List<TaskItem> findByStep_IdAndUser_Id(Long stepId, Long userId);

    List<TaskItem> findByStep_IdAndUser_IdAndArchivedFalse(Long stepId, Long userId);

    /** Next sort position in a step: a count, not a load of every sibling. */
    long countByStep_IdAndUser_IdAndArchivedFalse(Long stepId, Long userId);
}
