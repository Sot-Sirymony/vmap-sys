package com.visionmapping.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.visionmapping.entity.Obstacle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObstacleRepository extends JpaRepository<Obstacle, Long>, UserScopedRepository<Obstacle> {

    /** FR-73: anything recorded as missing for this dream or the work under it. */
    @Query("""
            select count(o) > 0 from Obstacle o
            left join o.relatedGoal g
            left join o.relatedStep s
            left join s.goal sg
            left join o.relatedTask t
            left join t.step ts
            left join ts.goal tg
            where o.user.id = :userId and o.archived = false
              and (o.relatedDream.id = :dreamId or g.dream.id = :dreamId
                   or sg.dream.id = :dreamId or tg.dream.id = :dreamId)
            """)
    boolean existsForDreamTree(@Param("userId") Long userId, @Param("dreamId") Long dreamId);

    /** V15 baselines (AHBO-03): obstacles in one status, across all participants. */
    long countByArchivedFalseAndStatus(com.visionmapping.entity.enums.ObstacleStatus status);

    /** V15 baselines (AHBO-03): open obstacles with a root cause, a solution or a linked task. */
    @Query("""
            select count(o) from Obstacle o
            where o.archived = false and o.status = :status
              and ((o.rootCause is not null and o.rootCause <> '') or (o.solution is not null and o.solution <> '')
                   or o.relatedTask is not null)
            """)
    long countWithPlan(@Param("status") com.visionmapping.entity.enums.ObstacleStatus status);
}
