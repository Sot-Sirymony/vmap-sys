package com.visionmapping.repository;

import com.visionmapping.entity.AiUsageLog;
import com.visionmapping.entity.enums.AiOutcome;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    @Query("select coalesce(sum(log.costUsd), 0) from AiUsageLog log where log.createdAt >= :from")
    BigDecimal totalCostSince(Instant from);

    long countByCreatedAtGreaterThanEqual(Instant from);

    long countByUser_IdAndCreatedAtGreaterThanEqualAndOutcomeIn(Long userId, Instant from, Collection<AiOutcome> outcomes);
}
