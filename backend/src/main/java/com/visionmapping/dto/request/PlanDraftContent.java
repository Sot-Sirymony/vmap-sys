package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * V11 Phase 2 (PB-FR-01 to PB-FR-03, PB-FR-30): a Build my plan draft for one
 * dream. Items with an existingId stand for goals or steps already in the plan:
 * they are never changed, only given new children. Every other item is new and
 * is saved only when the participant adds the plan (PB-FR-31).
 *
 * Fields may be blank while the participant is still writing; the full rules of
 * the normal create endpoints are applied when the plan is added.
 * requestId and edited (PB-FR-35) say which AI answer an item came from and
 * whether the participant changed it.
 */
public record PlanDraftContent(
        @Size(max = 15) List<@NotNull @Valid DraftGoal> goals,
        // V11 Phase 3 (PB-FR-09): clarifying answers given in this session, used by later suggestions.
        @Size(max = 6) List<@NotNull @Valid PlanClarification> clarifications,
        // V11 Phase 5 (PB-FR-23 to PB-FR-25): helpers to link or add as partners, and risks to add as obstacles.
        @Size(max = 10) List<@NotNull @Valid DraftHelper> helpers,
        @Size(max = 10) List<@NotNull @Valid DraftRisk> risks) {

    /**
     * A helper: partnerId links one of the participant's partners; without it a
     * new partner is added with status To Contact, named name (or the role).
     * linkKey is the draft key of the goal or step it is for.
     */
    public record DraftHelper(
            @Size(max = 40) String key,
            @Size(max = 120) String role,
            PartnerSupportType supportType,
            @Size(max = 180) String name,
            Long partnerId,
            // Shown in the guide only; the link is by partnerId, which is checked as the caller's own on add.
            @Size(max = 180) String partnerName,
            @Size(max = 40) String linkKey,
            Long requestId,
            boolean edited) {
    }

    /** A risk, added as an Open obstacle under linkKey's goal or step; helperKey names the helper who could fix it. */
    public record DraftRisk(
            @Size(max = 40) String key,
            @Size(max = 220) String title,
            @Size(max = 3000) String description,
            ObstacleType obstacleType,
            Severity severity,
            @Size(max = 3000) String solution,
            @Size(max = 40) String linkKey,
            @Size(max = 40) String helperKey,
            boolean fromFears,
            Long requestId,
            boolean edited) {
    }

    public record DraftGoal(
            @Size(max = 40) String key,
            Long existingId,
            @Size(max = 220) String title,
            @Size(max = 3000) String description,
            @Size(max = 3000) String successCriteria,
            LocalDate targetDate,
            Priority priority,
            boolean moonshot,
            Long requestId,
            boolean edited,
            boolean locked,
            @Size(max = 25) List<@NotNull @Valid DraftStep> steps) {
    }

    public record DraftStep(
            @Size(max = 40) String key,
            Long existingId,
            @Size(max = 220) String title,
            @Size(max = 3000) String description,
            @Size(max = 160) String owner,
            LocalDate targetDate,
            Priority priority,
            boolean complex,
            @Size(max = 1000) String resources,
            Long requestId,
            boolean edited,
            boolean locked,
            @Size(max = 15) List<@NotNull @Valid DraftTask> tasks,
            // V11 Phase 4 (PB-FR-12): effort, and the key of the step in this goal it starts after.
            @DecimalMin("0.00") @DecimalMax("999.99") BigDecimal estimatedHours,
            @Size(max = 40) String startsAfterKey) {
    }

    public record DraftTask(
            @Size(max = 40) String key,
            @Size(max = 220) String title,
            @Size(max = 160) String owner,
            LocalDate dueDate,
            Priority priority,
            @DecimalMin("0.00") @DecimalMax("999.99") BigDecimal estimatedHours,
            // V11 Phase 4 (PB-FR-14, PB-FR-35): the AI answer a task came from, and whether it was changed.
            Long requestId,
            boolean edited,
            boolean locked) {
    }
}
