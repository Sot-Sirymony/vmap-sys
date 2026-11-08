package com.visionmapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V9 (AI-FR-18, AI-FR-21): one week's AI summary for a coach, of one
 * participant (scope PARTICIPANT) or one cohort (scope COHORT). accurate is the
 * coach's verdict (AC-16).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_coach_summaries")
public class AiCoachSummary extends BaseAuditableEntity {

    public static final String SCOPE_PARTICIPANT = "PARTICIPANT";
    public static final String SCOPE_COHORT = "COHORT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id")
    private AppUser coach;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private AppUser participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "progress_text", length = 2000)
    private String progressText;

    @Column(name = "mood_text", length = 2000)
    private String moodText;

    @Column(name = "risks_text", length = 2000)
    private String risksText;

    @Column(name = "action_text", length = 2000)
    private String actionText;

    /** The coach's rating of this summary; null until the coach rates it. */
    private Boolean accurate;

    @Column(name = "rating_note", length = 1000)
    private String ratingNote;

    @Column(name = "rated_at")
    private Instant ratedAt;
}
