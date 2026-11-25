package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.AutomationChoice;
import com.visionmapping.entity.enums.RepeatCadence;
import com.visionmapping.entity.enums.RequirementSource;
import com.visionmapping.entity.enums.SpeedLever;
import com.visionmapping.entity.enums.StreamlineDecision;
import com.visionmapping.entity.enums.StreamlineStageState;
import com.visionmapping.entity.enums.StreamlineStatus;
import com.visionmapping.entity.enums.StreamlineVerdict;
import com.visionmapping.entity.enums.WorkStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.visionmapping.util.Copies;

/** V27 (BRD 27): the response bodies of the Streamline review API. */
public final class StreamlineResponses {

    private StreamlineResponses() {
    }

    public record Stage(int number, StreamlineStageState state, String reason, boolean unlocked) {
    }

    public record Item(
            Long id,
            String kind,
            Long stepId,
            Long taskId,
            Long parentStepId,
            String code,
            String title,
            WorkStatus status,
            boolean archived,
            BigDecimal estimatedHours,
            String requirementOwner,
            RequirementSource requirementSource,
            Long partnerId,
            String whyNeeded,
            StreamlineVerdict verdict,
            StreamlineDecision decision,
            Long mergeIntoItemId,
            String keepReason,
            boolean simplified,
            String simplifyNote,
            List<SpeedLever> speedLevers,
            AutomationChoice automation,
            Long delegatePartnerId,
            boolean archivedWithParent,
            Instant archivedAt,
            Instant restoredAt,
            BigDecimal cycleTimeDays,
            BigDecimal waitingDays,
            BigDecimal blockedDays,
            boolean repeatCandidate,
            Long repeatRuleId,
            // V32 (D-32-9): how often a REMIND item reminds.
            RepeatCadence remindCadence,
            Integer remindDay) {
        public Item {
            speedLevers = Copies.list(speedLevers);
        }
    }

    /** BRD 27 §2.4. Counts, never percentages (Q-27-7). */
    public record Summary(
            long deleted,
            long kept,
            long merged,
            long addedBack,
            int stepsBefore,
            int stepsAfter,
            int tasksBefore,
            int tasksAfter,
            BigDecimal hoursBefore,
            BigDecimal hoursAfter,
            BigDecimal cycleBefore,
            BigDecimal cycleTarget,
            BigDecimal cycleNow,
            long repeating,
            long reminders,
            long delegated,
            boolean nothingBroughtBack) {
    }

    public record Review(
            Long id,
            String code,
            String anchorType,
            Long goalId,
            Long stepId,
            String anchorCode,
            String anchorTitle,
            StreamlineStatus status,
            int currentStage,
            List<Stage> stages,
            BigDecimal baselineCycleDays,
            BigDecimal targetCycleDays,
            BigDecimal currentCycleDays,
            BigDecimal waitingDays,
            BigDecimal blockedDays,
            Instant startedAt,
            Instant completedAt,
            List<Item> items,
            Summary summary) {
        public Review {
            stages = Copies.list(stages);
            items = Copies.list(items);
        }
    }

    public record ListItem(
            Long id,
            String code,
            String anchorType,
            Long goalId,
            Long stepId,
            String anchorTitle,
            StreamlineStatus status,
            int currentStage,
            Instant startedAt,
            Instant completedAt,
            long deleted,
            long addedBack,
            int stepsBefore,
            int stepsAfter,
            int tasksBefore,
            int tasksAfter) {
    }

    /** §6.2: a goal or step worth streamlining, and why. */
    public record Candidate(String anchorType, Long anchorId, String code, String title, String reason, String detail) {
    }

    /** SR-FR-09: a repeating task rule. */
    public record RepeatRule(Long id, Long stepId, Long sourceTaskId, Long lastTaskId, String title, RepeatCadence cadence,
                             LocalDate nextDue, boolean active) {
    }

    /** The status history of one task. */
    public record StatusChange(WorkStatus fromStatus, WorkStatus toStatus, Instant changedAt) {
    }

    /** V32 (D-32-12): a dismissed suggestion. */
    public record DismissalView(String anchorType, Long anchorId, String rule, Instant dismissedAt) {
    }

    /** V32 (D-32-4): the result of a batch; failedAtPosition and error are set when an action failed. */
    public record BatchResult(Long batchId, int applied, int total, Integer failedAtPosition, String error, Review review) {
    }

    /** V32 (SF-FR-07): one change in a batch, for the Recent changes list. */
    public record BatchChange(String code, String action) {
    }

    /** V32 (SF-FR-07): one batch on a stage, newest first. */
    public record BatchView(Long id, int stage, int applied, int total, Integer failedAtPosition, Instant createdAt,
                            Instant undoneAt, boolean canUndo, List<BatchChange> changes) {
        public BatchView {
            changes = Copies.list(changes);
        }
    }

    /** V32 (BR-32-6): an item undo left as it is, because it was changed after the batch. */
    public record UndoSkipped(String code, String reason) {
    }

    /** V32 (D-32-5): the result of undoing a batch. */
    public record UndoResult(int undone, List<UndoSkipped> skipped, Review review) {
        public UndoResult {
            skipped = Copies.list(skipped);
        }
    }
}
