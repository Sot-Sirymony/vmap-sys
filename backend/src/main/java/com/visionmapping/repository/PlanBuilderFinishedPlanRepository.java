package com.visionmapping.repository;

import com.visionmapping.entity.PlanBuilderFinishedPlan;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlanBuilderFinishedPlanRepository extends JpaRepository<PlanBuilderFinishedPlan, Long> {

    /** V11 (PB-FR-36): count, total requests and total cost of plans finished since a time. */
    @Query("""
            select count(plan), coalesce(sum(plan.aiRequests), 0), coalesce(sum(plan.aiCostUsd), 0)
            from PlanBuilderFinishedPlan plan where plan.finishedAt >= :from
            """)
    List<Object[]> totalsSince(Instant from);
}
