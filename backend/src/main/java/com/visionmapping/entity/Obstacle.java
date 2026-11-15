package com.visionmapping.entity;

import com.visionmapping.entity.enums.ExpectationAgreement;
import com.visionmapping.entity.enums.ObstaclePlacement;
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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "obstacles")
public class Obstacle extends BaseAuditableEntity implements PlanLinked {

    // V17 D-17-1: a plan obstacle is anchored to a step or a task. The goal
    // and dream columns are then derived from that anchor, never chosen. They
    // stay as columns so the Obstacles page and dashboard can filter by dream
    // or goal, and for unplaced rows (older data and exempt partner conflicts).

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

    // Same pattern as Dream.letterRank (FR-57): an optional single
    // uppercase letter, a label not a strict order — ties allowed, never
    // gates anything.
    @Column(name = "letter_rank", length = 1)
    private String letterRank;

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

    // FR-61.1: the specific expectation behind the conflict, and whether it
    // was ever actually agreed to. Diagnostic only (BR-50) — never gates
    // status, same as the FR-54 worksheet fields above.
    @Column(name = "conflict_expectation", length = 2000)
    private String conflictExpectation;

    @Enumerated(EnumType.STRING)
    @Column(name = "conflict_expectation_agreed", length = 10)
    private ExpectationAgreement conflictExpectationAgreed;

    // FR-61.2: set exactly once, the first time "Release this expectation"
    // is used; never re-fires or reverses (BR-50).
    @Column(name = "expectation_released_at")
    private Instant expectationReleasedAt;

    // FR-62.1: a four-item conduct checklist, required before a PARTNER-type
    // obstacle can be marked Resolved (BR-51) — completeness gates the
    // transition, never the answers themselves. Boolean, not boolean: null
    // means "not answered yet".
    @Column(name = "conflict_no_character_attacks")
    private Boolean conflictNoCharacterAttacks;

    @Column(name = "conflict_stayed_on_incident")
    private Boolean conflictStayedOnIncident;

    @Column(name = "conflict_no_threats_or_sarcasm")
    private Boolean conflictNoThreatsOrSarcasm;

    @Column(name = "conflict_defined_win_win")
    private Boolean conflictDefinedWinWin;

    // FR-60.1: incoming-criticism triage on PARTNER-type obstacles.
    // Diagnostic only (BR-49) — never gates status, same as every other
    // worksheet field above.
    @Column(name = "criticism_overstated", length = 2000)
    private String criticismOverstated;

    @Column(name = "criticism_delivery", length = 2000)
    private String criticismDelivery;

    @Column(name = "criticism_substance", length = 2000)
    private String criticismSubstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_partner_id")
    private Partner requiredPartner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ObstacleStatus status;

    @Column(nullable = false)
    private boolean archived;

    /** V17 BL-FR-01: the most specific place in the plan this obstacle sits. */
    public ObstaclePlacement placement() {
        if (relatedTask != null) {
            return ObstaclePlacement.TASK;
        }
        return relatedStep != null ? ObstaclePlacement.STEP : ObstaclePlacement.NONE;
    }

    /**
     * V17 BL-FR-04: a partner conflict worked through the conflict
     * worksheet, expectation, conduct checklist or criticism triage (FR-54,
     * FR-60 to FR-62) is about a relationship, not one step, so it may stay
     * unplaced.
     */
    public boolean isPlacementExempt() {
        if (obstacleType != ObstacleType.PARTNER) {
            return false;
        }
        boolean checklistStarted = conflictNoCharacterAttacks != null || conflictStayedOnIncident != null
                || conflictNoThreatsOrSarcasm != null || conflictDefinedWinWin != null;
        return checklistStarted || conflictExpectationAgreed != null || anyFilled(conflictIncident, conflictCost,
                conflictOtherPerspective, conflictLesson, conflictPrivateNote, conflictNextAction,
                conflictExpectation, criticismOverstated, criticismDelivery, criticismSubstance);
    }

    /** V17 BL-FR-09: anchored, but the step or task it sits on was archived. */
    public boolean isAnchorArchived() {
        return (relatedTask != null && relatedTask.isArchived())
                || (relatedStep != null && relatedStep.isArchived());
    }

    /**
     * V17 BL-FR-02: puts the obstacle on a task (whose step wins) or a step,
     * and derives the goal and dream from it. The caller has checked that a
     * given task belongs to a given step.
     */
    public void anchorTo(@Nullable VisionStep step, @Nullable TaskItem task) {
        relatedTask = task;
        relatedStep = task != null ? task.getStep() : step;
        syncParentsFromAnchor();
    }

    /**
     * Leaves the obstacle off the plan's steps and tasks, keeping a goal or
     * dream only as context (BL-FR-04, BL-FR-06). A goal brings its own dream,
     * so the two can never disagree.
     */
    public void leaveUnplaced(@Nullable Dream dream, @Nullable Goal goal) {
        relatedTask = null;
        relatedStep = null;
        relatedGoal = goal;
        relatedDream = goal != null ? goal.getDream() : dream;
    }

    /**
     * V17 BL-FR-02, BL-FR-08: re-reads the step, goal and dream from the
     * anchor, for example after the task, step or goal moved. An unplaced
     * obstacle is left as it is.
     */
    public void syncParentsFromAnchor() {
        if (relatedTask != null) {
            relatedStep = relatedTask.getStep();
        }
        if (relatedStep != null) {
            relatedGoal = relatedStep.getGoal();
            relatedDream = relatedGoal == null ? null : relatedGoal.getDream();
        }
    }

    private static boolean anyFilled(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
