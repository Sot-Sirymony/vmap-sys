package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.AutomationChoice;
import com.visionmapping.entity.enums.RequirementSource;
import com.visionmapping.entity.enums.SpeedLever;
import com.visionmapping.entity.enums.StreamlineVerdict;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.visionmapping.util.Copies;
import com.visionmapping.entity.enums.RepeatCadence;

/** V27 (BRD 27): the request bodies of the Streamline review API. A null field leaves the stored value as it is. */
public final class StreamlineRequests {

    private StreamlineRequests() {
    }

    /** SR-FR-01: start a review of a goal or a step (exactly one). */
    public record Start(Long goalId, Long stepId) {
    }

    /**
     * Records decisions on one item. Each group of fields opens with its stage:
     * question (1), keep reason (2), levers (4), automation (5).
     */
    public record UpdateItem(
            @Size(max = 120) String requirementOwner,
            RequirementSource requirementSource,
            Long partnerId,
            @Size(max = 300) String whyNeeded,
            StreamlineVerdict verdict,
            Boolean keep,
            @Size(max = 300) String keepReason,
            @Size(max = 6) List<SpeedLever> speedLevers,
            AutomationChoice automation,
            Long delegatePartnerId,
            // V32 (D-32-9): with automation REMIND; day is the weekday (1 to 7) or the day of the month (1 to 28).
            RepeatCadence remindCadence,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(28) Integer remindDay) {
        public UpdateItem {
            speedLevers = Copies.list(speedLevers);
        }
    }

    /** SR-FR-03 (Q-27-2): the same stage 1 answers for several items at once. */
    public record BulkQuestion(
            @NotEmpty @Size(max = 500) List<Long> itemIds,
            @Size(max = 120) String requirementOwner,
            RequirementSource requirementSource,
            @Size(max = 300) String whyNeeded,
            StreamlineVerdict verdict) {
        public BulkQuestion {
            itemIds = Copies.list(itemIds);
        }
    }

    /** BR-27-8: confirm that a goal or step may be left with nothing open. */
    public record Delete(boolean confirmEmpty) {
    }

    /** BR-27-6: merge this item into another item of the same kind. */
    public record Merge(@NotNull Long targetItemId, boolean confirmEmpty) {
    }

    /** Stage 3: simplify a surviving step or task in place. */
    public record Simplify(
            @Size(max = 220) String title,
            @Size(max = 3000) String description,
            @Size(max = 120) String owner,
            @DecimalMin("0") @DecimalMax("10000") BigDecimal estimatedHours,
            Boolean markSimple,
            @Size(max = 300) String note) {
    }

    /** D-27-3: a stage marked Not needed keeps its reason. */
    /** V32 D-32-1: a dismissed suggestion card, removed from the saved set. */
    public record DismissSuggestion(@NotNull Long itemId,
                                    @NotNull @jakarta.validation.constraints.Pattern(regexp = "[A-Z]{3,10}") String action,
                                    Long targetItemId) {
    }

    public record NotNeeded(@NotNull @Size(min = 10, max = 500) String reason) {
    }

    /** Stage 4: the target cycle time in days. */
    public record Target(@NotNull @DecimalMin("0.1") @DecimalMax("3650") BigDecimal targetCycleDays) {
    }

    /** SR-FR-09: make a task repeat, from the repeat rules API. */
    public record CreateRule(Long taskId, RepeatCadence cadence, LocalDate firstDue) {
    }

    /** SR-FR-09: change a rule's cadence, or stop or restart it. */
    public record UpdateRule(RepeatCadence cadence, Boolean active) {
    }

    /**
     * V32 (D-32-4): one action of a batch, in the shape of the single actions.
     * UPDATE carries update; MERGE carries targetItemId; SIMPLIFY carries
     * simplify; REPEAT carries cadence; DELETE needs nothing more.
     */
    public record BatchAction(
            @NotNull @jakarta.validation.constraints.Pattern(regexp = "UPDATE|DELETE|MERGE|SIMPLIFY|REPEAT") String type,
            @NotNull Long itemId,
            @jakarta.validation.Valid UpdateItem update,
            Long targetItemId,
            @jakarta.validation.Valid Simplify simplify,
            RepeatCadence cadence) {
    }

    /** V32 (D-32-4, BR-32-4): Apply selected or Apply all; at most 50 actions, for the current stage. */
    public record ApplyBatch(
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5) int stage,
            @NotEmpty @Size(max = 50) List<@NotNull @jakarta.validation.Valid BatchAction> actions) {
        public ApplyBatch {
            actions = Copies.list(actions);
        }
    }

    /** V32 (D-32-12): one dismissed suggestion; rule is the candidate's rule, or AI_PICK. */
    public record Dismissal(@NotNull @jakarta.validation.constraints.Pattern(regexp = "GOAL|STEP") String anchorType,
                            @NotNull Long anchorId,
                            @NotNull @jakarta.validation.constraints.Pattern(regexp = "[A-Z_]{3,30}") String rule) {
    }

    /** V32 (D-32-12): dismissals to save; a list, so the ones kept in a browser can move in one request. */
    public record Dismissals(@NotEmpty @Size(max = 100) List<@NotNull @jakarta.validation.Valid Dismissal> dismissals) {
        public Dismissals {
            dismissals = Copies.list(dismissals);
        }
    }

    /** Stage 5 and SR-FR-09: make a task repeat. */
    public record Repeat(@NotNull RepeatCadence cadence, LocalDate firstDue) {
    }
}
