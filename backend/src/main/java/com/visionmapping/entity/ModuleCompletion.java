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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** FR-72: one user's finished module. Later saves update rows but keep completedAt. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "module_completions")
public class ModuleCompletion extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "module_number", nullable = false)
    private Integer moduleNumber;

    @Column(name = "rows_json", length = 20000)
    private String rowsJson;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    /** V9 (v0.1 FR-11): the coach's 1 to 5 rating of a pitch. Cleared when the pitch changes. */
    @Column(name = "coach_rating")
    private Integer coachRating;

    @Column(name = "coach_feedback", length = 2000)
    private String coachFeedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_rated_by")
    private AppUser coachRatedBy;

    @Column(name = "coach_rated_at")
    private Instant coachRatedAt;

    public void clearCoachRating() {
        coachRating = null;
        coachFeedback = null;
        coachRatedBy = null;
        coachRatedAt = null;
    }
}
