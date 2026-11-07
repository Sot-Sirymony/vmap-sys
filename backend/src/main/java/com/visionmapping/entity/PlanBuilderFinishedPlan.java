package com.visionmapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V11 (PB-FR-36): one plan added from Build my plan, with the AI requests its
 * session used and their cost. Counts only. Ids are kept as plain columns:
 * a deleted account or dream only loses its link (see AccountService).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "plan_builder_finished_plans")
public class PlanBuilderFinishedPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "dream_id")
    private Long dreamId;

    @Column(name = "ai_requests", nullable = false)
    private int aiRequests;

    @Column(name = "ai_cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal aiCostUsd;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "session_key", length = 36)
    private String sessionKey;
}
