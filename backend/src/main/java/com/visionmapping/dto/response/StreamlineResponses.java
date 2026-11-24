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
            Long repeatRuleId) {
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
}
