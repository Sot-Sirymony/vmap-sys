package com.visionmapping.entity;

import com.visionmapping.entity.enums.ReviewType;
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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reviews")
public class Review extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 40)
    private ReviewType reviewType;

    @Column(name = "review_date", nullable = false)
    private LocalDateTime reviewDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_vision_area_id")
    private VisionArea relatedVisionArea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_dream_id")
    private Dream relatedDream;

    @Column(length = 3000)
    private String summary;

    @Column(name = "completed_tasks", length = 3000)
    private String completedTasks;

    @Column(name = "delayed_tasks", length = 3000)
    private String delayedTasks;

    @Column(name = "blocked_tasks", length = 3000)
    private String blockedTasks;

    @Column(name = "lessons_learned", length = 3000)
    private String lessonsLearned;

    @Column(name = "next_actions", length = 3000)
    private String nextActions;

    // Diligence checklist (FR-16): all five answers null = checklist skipped;
    // all five non-null = answered. Mixed states are rejected by the service.
    @Column(name = "diligence_clear_vision")
    private Boolean diligenceClearVision;

    @Column(name = "diligence_worked_plan")
    private Boolean diligenceWorkedPlan;

    @Column(name = "diligence_used_leverage")
    private Boolean diligenceUsedLeverage;

    @Column(name = "diligence_priority_first")
    private Boolean diligencePriorityFirst;

    @Column(name = "diligence_smarter_route")
    private Boolean diligenceSmarterRoute;

    @Column(name = "diligence_note", length = 2000)
    private String diligenceNote;

    @Column(nullable = false)
    private boolean archived;
}
