package com.visionmapping.repository;

import com.visionmapping.entity.AiUsageLog;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    @Query("select coalesce(sum(log.costUsd), 0) from AiUsageLog log where log.createdAt >= :from")
    BigDecimal totalCostSince(Instant from);

    long countByCreatedAtGreaterThanEqual(Instant from);
}
