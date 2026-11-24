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
            Long delegatePartnerId) {
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

    /** Stage 5 and SR-FR-09: make a task repeat. */
    public record Repeat(@NotNull RepeatCadence cadence, LocalDate firstDue) {
    }
}
