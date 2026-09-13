package com.visionmapping.entity;

import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Severity;
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
@Table(name = "obstacles")
public class Obstacle extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_dream_id")
    private Dream relatedDream;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_goal_id")
    private Goal relatedGoal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_step_id")
    private VisionStep relatedStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_task_id")
    private TaskItem relatedTask;

    @Column(nullable = false, length = 220)
    private String title;

    @Column(length = 3000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "obstacle_type", nullable = false, length = 40)
    private ObstacleType obstacleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Severity severity;

    @Column(length = 3000)
    private String solution;

    // FR-32: diagnostic metadata only — required before Resolved (BR-25).
    @Column(name = "root_cause", length = 3000)
    private String rootCause;

    // FR-32: one alternative per line — at least three required before
    // Accepted (BR-26).
    @Column(name = "creative_alternatives", length = 3000)
    private String creativeAlternatives;

    // FR-54.1: a guided worksheet for PARTNER-type obstacles. Diagnostic
    // only (FR-54.4) — never gates a status transition, unlike rootCause/
    // creativeAlternatives above.
    @Column(name = "conflict_incident", length = 2000)
    private String conflictIncident;

    @Column(name = "conflict_cost", length = 2000)
    private String conflictCost;

    @Column(name = "conflict_other_perspective", length = 2000)
    private String conflictOtherPerspective;

    @Column(name = "conflict_lesson", length = 2000)
    private String conflictLesson;

    // FR-54.2: BR-43 — this field is never wired into Excel export, in
    // either direction. See ExcelService: the Obstacles sheet lists only
    // the columns it explicitly names, so keeping this one out of that list
    // is the whole enforcement — there is no separate exclusion to bypass.
    @Column(name = "conflict_private_note", length = 2000)
    private String conflictPrivateNote;

    @Column(name = "conflict_next_action", length = 2000)
    private String conflictNextAction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_partner_id")
    private Partner requiredPartner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ObstacleStatus status;

    @Column(nullable = false)
    private boolean archived;
}
