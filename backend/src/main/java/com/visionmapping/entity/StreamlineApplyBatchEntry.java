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

/**
 * V32 (D-32-4): one action of a batch, with what it changed recorded before
 * the change (beforeState, JSON) so the batch can be undone.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "streamline_apply_batch_entries")
public class StreamlineApplyBatchEntry extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private StreamlineApplyBatch batch;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 20)
    private String action;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private StreamlineItem item;

    @Column(name = "before_state", nullable = false, columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "item_updated_at")
    private Instant itemUpdatedAt;

    @Column(name = "target_updated_at")
    private Instant targetUpdatedAt;

    @Column(name = "other_updated_at")
    private Instant otherUpdatedAt;
}
