package com.visionmapping.repository;

import com.visionmapping.entity.TaskRepeatRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** V27 SR-FR-09: repeating task rules. */
public interface TaskRepeatRuleRepository extends JpaRepository<TaskRepeatRule, Long> {

    List<TaskRepeatRule> findByUser_IdOrderByIdAsc(Long userId);

    Optional<TaskRepeatRule> findByIdAndUser_Id(Long id, Long userId);

    Optional<TaskRepeatRule> findFirstBySourceTask_IdAndUser_IdAndActiveTrue(Long sourceTaskId, Long userId);

    /** Every active rule, for the job; it checks each rule's current task itself. */
    List<TaskRepeatRule> findByActiveTrue();
}
