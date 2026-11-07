package com.visionmapping.repository;

import com.visionmapping.entity.AiUsageLog;
import com.visionmapping.entity.enums.AiOutcome;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    @Query("select coalesce(sum(log.costUsd), 0) from AiUsageLog log where log.createdAt >= :from")
    BigDecimal totalCostSince(Instant from);

    long countByCreatedAtGreaterThanEqual(Instant from);

    /**
     * V11 (PB-FR-36): per feature since a time: feature, requests, suggested
     * items, added, edited, ratings, thumbs up, cost.
     */
    @Query("""
            select log.feature, count(log), coalesce(sum(log.suggestedItems), 0), coalesce(sum(log.addedItems), 0),
                   coalesce(sum(log.editedItems), 0), sum(case when log.rating is not null then 1 else 0 end),
                   sum(case when log.rating = 1 then 1 else 0 end), coalesce(sum(log.costUsd), 0)
            from AiUsageLog log
            where log.createdAt >= :from and log.feature in :features
            group by log.feature
            """)
    List<Object[]> planMetricsSince(Instant from, Collection<String> features);

    long countByUser_IdAndCreatedAtGreaterThanEqualAndOutcomeInAndFeatureNotIn(Long userId, Instant from,
            Collection<AiOutcome> outcomes, Collection<String> excludedFeatures);

    /** V11 (PB-FR-36): the requests one Build my plan session made, and their cost. */
    @Query("""
            select count(log), coalesce(sum(log.costUsd), 0) from AiUsageLog log where log.planSessionKey = :sessionKey
            """)
    List<Object[]> sessionTotals(String sessionKey);

    /**
     * V11 (PB-FR-36): per feature, the requests and cost of the sessions of
     * plans finished since a time: feature, requests, cost.
     */
    @Query("""
            select log.feature, count(log), coalesce(sum(log.costUsd), 0)
            from AiUsageLog log, PlanBuilderFinishedPlan plan
            where log.planSessionKey = plan.sessionKey and plan.finishedAt >= :from
            group by log.feature
            """)
    List<Object[]> finishedSessionsByFeatureSince(Instant from);
}
