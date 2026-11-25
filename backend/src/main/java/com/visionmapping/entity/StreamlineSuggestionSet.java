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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V32 (D-32-1, D-32-2): the last checked AI suggestions for one stage of one
 * Streamline review. cards is JSON; it is only ever shown back to its owner
 * and never read as plan data.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "streamline_suggestion_sets")
public class StreamlineSuggestionSet extends BaseAuditableEntity {

    public static final String SOURCE_STAGE = "STAGE";
    public static final String SOURCE_DRAFT_ALL = "DRAFT_ALL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private StreamlineReview review;

    @Column(nullable = false)
    private int stage;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(length = 60)
    private String model;

    @Column(name = "request_id")
    private Long requestId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String cards;

    /** V32 phase 3: the review's current stage when the set was made. */
    @Column(name = "drafted_at_stage")
    private Integer draftedAtStage;
}
