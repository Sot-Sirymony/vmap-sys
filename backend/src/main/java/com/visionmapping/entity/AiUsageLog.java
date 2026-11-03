package com.visionmapping.entity;

import com.visionmapping.entity.enums.AiOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI-NFR-14: one AI request, with its cost and the participant's rating
 * (AI-FR-06), but never the conversation text, which stays in the
 * participant's own history (AI-FR-25).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_usage_logs")
public class AiUsageLog extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null once the person deletes their account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, length = 40)
    private String feature;

    @Column(length = 60)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal costUsd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiOutcome outcome;

    /** AI-FR-06: 1 for thumbs up, -1 for thumbs down, null if not rated. */
    private Integer rating;

    @Column(name = "rating_comment", length = 1000)
    private String ratingComment;
}
