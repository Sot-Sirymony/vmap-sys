package com.visionmapping.repository;

import com.visionmapping.entity.GoalSynergyLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalSynergyLinkRepository extends JpaRepository<GoalSynergyLink, Long> {

    /** Every link touching a goal, from either side — a link is read both ways. */
    @Query("select l from GoalSynergyLink l "
            + "where l.user.id = :userId and (l.goal.id = :goalId or l.relatedGoal.id = :goalId)")
    List<GoalSynergyLink> findForGoal(@Param("userId") Long userId, @Param("goalId") Long goalId);

    /**
     * Pairs are stored normalised (lower goal id first), so a duplicate check is
     * a plain lookup on the normalised pair.
     */
    boolean existsByUser_IdAndGoal_IdAndRelatedGoal_Id(Long userId, Long goalId, Long relatedGoalId);

    List<GoalSynergyLink> findByUser_Id(Long userId);
}
