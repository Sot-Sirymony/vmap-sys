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

/** V32 (D-32-4, D-32-5): one Apply selected or Apply all on a Streamline review stage, which can be undone as a whole. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "streamline_apply_batches")
public class StreamlineApplyBatch extends BaseAuditableEntity {

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

    @Column(nullable = false)
    private int total;

    @Column(name = "applied_count", nullable = false)
    private int appliedCount;

    @Column(name = "failed_at_position")
    private Integer failedAtPosition;

    @Column(length = 500)
    private String error;

    @Column(name = "undone_at")
    private Instant undoneAt;
}
