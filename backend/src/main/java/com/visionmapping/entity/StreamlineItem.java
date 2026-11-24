package com.visionmapping.entity;

import com.visionmapping.entity.enums.AutomationChoice;
import com.visionmapping.entity.enums.RequirementSource;
import com.visionmapping.entity.enums.StreamlineDecision;
import com.visionmapping.entity.enums.StreamlineVerdict;
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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** V27: one step or task in a Streamline review's scope, with the decisions made about it. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "streamline_items")
public class StreamlineItem extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private StreamlineReview review;

    /** Exactly one of step and task is set. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id")
    private VisionStep step;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private TaskItem task;

    // Stage 1 (D-27-6).
    @Column(name = "requirement_owner", length = 120)
    private String requirementOwner;
    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_source", length = 20)
    private RequirementSource requirementSource;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private Partner partner;
    @Column(name = "why_needed", length = 300)
    private String whyNeeded;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StreamlineVerdict verdict;

    // Stage 2.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StreamlineDecision decision;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merge_into_item_id")
    private StreamlineItem mergeInto;
    @Column(name = "keep_reason", length = 300)
    private String keepReason;

    // Stage 3.
    @Column(nullable = false)
    private boolean simplified;
    @Column(name = "simplify_note", length = 300)
    private String simplifyNote;

    // Stage 4: comma-separated SpeedLever names.
    @Column(name = "speed_levers", length = 200)
    private String speedLevers;

    // Stage 5.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AutomationChoice automation;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegate_partner_id")
    private Partner delegatePartner;

    /** A task archived because its step was deleted in this review; restoring the step brings it back (BR-27-5). */
    @Column(name = "archived_with_parent", nullable = false)
    private boolean archivedWithParent;
    @Column(name = "archived_at")
    private Instant archivedAt;
    @Column(name = "restored_at")
    private Instant restoredAt;

    public boolean isStep() {
        return step != null;
    }

    public String code() {
        return isStep() ? step.getCode() : task.getCode();
    }

    public String title() {
        return isStep() ? step.getTitle() : task.getTitle();
    }

    /** Whether the underlying step or task is archived right now. */
    public boolean targetArchived() {
        return isStep() ? step.isArchived() : task.isArchived();
    }
}
