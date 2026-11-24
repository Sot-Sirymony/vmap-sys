package com.visionmapping.entity;

import com.visionmapping.entity.enums.StreamlineStageState;
import com.visionmapping.entity.enums.StreamlineStatus;
import com.visionmapping.exception.BusinessRuleException;
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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * V27 (BRD 27): a Streamline review of one goal or one step, taken through five
 * stages in order — question, delete, simplify, speed up, automate (D-27-3).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "streamline_reviews")
public class StreamlineReview extends BaseAuditableEntity {

    public static final int STAGES = 5;
    public static final int MIN_REASON = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** D-27-2: exactly one of goal and step is set. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id")
    private VisionStep step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StreamlineStatus status;

    @Column(name = "current_stage", nullable = false)
    private int currentStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage1_state", nullable = false, length = 20)
    private StreamlineStageState stage1State;
    @Enumerated(EnumType.STRING)
    @Column(name = "stage2_state", nullable = false, length = 20)
    private StreamlineStageState stage2State;
    @Enumerated(EnumType.STRING)
    @Column(name = "stage3_state", nullable = false, length = 20)
    private StreamlineStageState stage3State;
    @Enumerated(EnumType.STRING)
    @Column(name = "stage4_state", nullable = false, length = 20)
    private StreamlineStageState stage4State;
    @Enumerated(EnumType.STRING)
    @Column(name = "stage5_state", nullable = false, length = 20)
    private StreamlineStageState stage5State;

    @Column(name = "stage1_reason", length = 500)
    private String stage1Reason;
    @Column(name = "stage2_reason", length = 500)
    private String stage2Reason;
    @Column(name = "stage3_reason", length = 500)
    private String stage3Reason;
    @Column(name = "stage4_reason", length = 500)
    private String stage4Reason;
    @Column(name = "stage5_reason", length = 500)
    private String stage5Reason;

    @Column(name = "baseline_steps", nullable = false)
    private int baselineSteps;
    @Column(name = "baseline_tasks", nullable = false)
    private int baselineTasks;
    @Column(name = "baseline_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal baselineHours;

    @Column(name = "result_steps")
    private Integer resultSteps;
    @Column(name = "result_tasks")
    private Integer resultTasks;
    @Column(name = "result_hours", precision = 10, scale = 2)
    private BigDecimal resultHours;

    @Column(name = "baseline_cycle_days", precision = 10, scale = 1)
    private BigDecimal baselineCycleDays;
    @Column(name = "target_cycle_days", precision = 10, scale = 1)
    private BigDecimal targetCycleDays;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public boolean isOpen() {
        return status == StreamlineStatus.OPEN;
    }

    public StreamlineStageState stageState(int stage) {
        return switch (stage) {
            case 1 -> stage1State;
            case 2 -> stage2State;
            case 3 -> stage3State;
            case 4 -> stage4State;
            case 5 -> stage5State;
            default -> throw new BusinessRuleException("A Streamline review has stages 1 to 5.");
        };
    }

    @Nullable
    public String stageReason(int stage) {
        return switch (stage) {
            case 1 -> stage1Reason;
            case 2 -> stage2Reason;
            case 3 -> stage3Reason;
            case 4 -> stage4Reason;
            case 5 -> stage5Reason;
            default -> null;
        };
    }

    /** BR-27-3: a stage is reached once every earlier stage is done or not needed. */
    public boolean isUnlocked(int stage) {
        return stage >= 1 && stage <= currentStage;
    }

    /**
     * Finishes the current stage (D-27-3: in order only). Not needed needs a
     * reason of at least {@link #MIN_REASON} characters, which is kept.
     */
    public void finishStage(int stage, StreamlineStageState state, @Nullable String reason) {
        if (!isOpen()) {
            throw new BusinessRuleException("This review is closed.");
        }
        if (stage != currentStage) {
            throw new BusinessRuleException("Finish stage " + currentStage + " first. The stages go in order.");
        }
        if (state == StreamlineStageState.NOT_NEEDED && (reason == null || reason.strip().length() < MIN_REASON)) {
            throw new BusinessRuleException("Say in a few words why this stage is not needed (at least " + MIN_REASON + " characters).");
        }
        String kept = state == StreamlineStageState.NOT_NEEDED ? reason.strip() : null;
        switch (stage) {
            case 1 -> { stage1State = state; stage1Reason = kept; }
            case 2 -> { stage2State = state; stage2Reason = kept; }
            case 3 -> { stage3State = state; stage3Reason = kept; }
            case 4 -> { stage4State = state; stage4Reason = kept; }
            case 5 -> { stage5State = state; stage5Reason = kept; }
            default -> throw new BusinessRuleException("A Streamline review has stages 1 to 5.");
        }
        if (stage < STAGES) {
            currentStage = stage + 1;
        }
    }

    public void requireUnlocked(int stage) {
        if (!isOpen()) {
            throw new BusinessRuleException("This review is closed.");
        }
        if (!isUnlocked(stage)) {
            throw new BusinessRuleException("Stage " + stage + " opens after stage " + (stage - 1) + " is done. The stages go in order.");
        }
    }

    /** The anchor's title, for lists and the export. */
    public String anchorTitle() {
        return goal != null ? goal.getTitle() : step.getTitle();
    }
}
