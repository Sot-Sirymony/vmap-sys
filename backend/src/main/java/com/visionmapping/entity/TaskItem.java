package com.visionmapping.entity;

import com.visionmapping.entity.enums.EnergyDemand;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * A small executable action under one step (Core Business Rules 4 and 5). The
 * task keeps its own rules: a Blocked task must have a blocker reason (Rule 6),
 * and a Completed task is at 100% with a completion time. Lifecycle fields change
 * only through {@link #update}, {@link #changeStatus}, {@link #recordProgress},
 * {@link #archive} and {@link #restore}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "task_items")
public class TaskItem extends BaseAuditableEntity {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", nullable = false)
    private VisionStep step;

    @Column(nullable = false, length = 220)
    private String title;

    @Column(length = 3000)
    private String description;

    @Column(nullable = false, length = 160)
    private String owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Priority priority;

    // Same pattern as Dream.letterRank (FR-57): an optional single
    // uppercase letter, a label not a strict order — ties allowed, never
    // gates anything.
    @Column(name = "letter_rank", length = 1)
    private String letterRank;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Setter(AccessLevel.NONE)
    private WorkStatus status;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    @Setter(AccessLevel.NONE)
    private BigDecimal progressPercent;

    @Column(name = "estimated_hours", precision = 8, scale = 2)
    private BigDecimal estimatedHours;

    @Column(name = "actual_hours", precision = 8, scale = 2)
    private BigDecimal actualHours;

    @Column(name = "blocker_reason", length = 2000)
    @Setter(AccessLevel.NONE)
    private String blockerReason;

    @Column(name = "next_action", length = 2000)
    private String nextAction;

    // FR-34.1: optional energy demand. Nullable — null reads as NEUTRAL (BR-27).
    @Enumerated(EnumType.STRING)
    @Column(name = "energy_demand", length = 16)
    private EnergyDemand energyDemand;

    @Column(name = "completed_at")
    @Setter(AccessLevel.NONE)
    private Instant completedAt;

    // Display order among this step's tasks, for the Vision Map tree's
    // drag-and-drop reorder. 0-based; null on legacy rows until the first
    // reorder (or a fresh create) sets it — callers fall back to id order.
    @Column(name = "sort_order")
    private Integer sortOrder;

    // The participant's overall order on the Tasks page, across all steps
    // ("this first, that second"). Independent of sortOrder above, so moving
    // a task on the Tasks page never reshuffles a step on the Vision Map.
    // 0-based; null until the first Tasks-page reorder (sorts last, by id).
    @Column(name = "list_order")
    private Integer listOrder;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private boolean archived;

    /**
     * Everything a participant edits on a task, already checked for format by
     * the service (progress normalised to 0–100, step owned by the user).
     */
    public record Details(VisionStep step, String title, String description, String owner, Priority priority,
                          String letterRank, LocalDate startDate, LocalDate dueDate, WorkStatus status,
                          BigDecimal progressPercent, BigDecimal estimatedHours, BigDecimal actualHours,
                          String blockerReason, String nextAction, EnergyDemand energyDemand) {
    }

    /**
     * Replaces every editable field. The new values are checked first, so a
     * refused update leaves the task exactly as it was (Effective Java Item 76).
     */
    public void update(Details details, Clock clock) {
        Objects.requireNonNull(details, "details");
        requireBlockerReason(details.status(), details.blockerReason());
        step = details.step();
        title = details.title();
        description = details.description();
        owner = details.owner();
        priority = details.priority();
        letterRank = details.letterRank();
        startDate = details.startDate();
        dueDate = details.dueDate();
        status = details.status();
        progressPercent = details.progressPercent();
        estimatedHours = details.estimatedHours();
        actualHours = details.actualHours();
        blockerReason = details.blockerReason();
        nextAction = details.nextAction();
        energyDemand = details.energyDemand();
        applyCompletionRule(clock);
    }

    /** A status-only change, such as a move on the Tasks board. */
    public void changeStatus(WorkStatus newStatus, Clock clock) {
        requireBlockerReason(Objects.requireNonNull(newStatus, "status"), blockerReason);
        status = newStatus;
        applyCompletionRule(clock);
    }

    /** Applies the same rules to a task made with the builder, before its first save. */
    public void checkNewTask(Clock clock) {
        requireBlockerReason(status, blockerReason);
        applyCompletionRule(clock);
    }

    /**
     * A progress-log entry moved the task. Reaching 100% completes it and
     * stamps the completion time.
     */
    public void recordProgress(BigDecimal percent, Clock clock) {
        progressPercent = Objects.requireNonNull(percent, "percent");
        if (ONE_HUNDRED.compareTo(percent) == 0) {
            status = WorkStatus.COMPLETED;
            completedAt = Instant.now(clock);
        }
    }

    /** Rule 12: hidden, never deleted. */
    public void archive() {
        archived = true;
    }

    public void restore() {
        archived = false;
    }

    /** CLAUDE.md Core Business Rule 6: a Blocked task must say what blocks it. */
    private static void requireBlockerReason(WorkStatus status, String reason) {
        if (status == WorkStatus.BLOCKED && (reason == null || reason.isBlank())) {
            throw new BusinessRuleException("Blocked tasks must include a blocker reason.");
        }
    }

    /** A Completed task is at 100% and keeps its first completion time; any other status has none. */
    private void applyCompletionRule(Clock clock) {
        if (status == WorkStatus.COMPLETED) {
            progressPercent = ONE_HUNDRED;
            if (completedAt == null) {
                completedAt = Instant.now(Objects.requireNonNull(clock, "clock"));
            }
        } else {
            completedAt = null;
        }
    }

    /** The id of its step's area, or null while the link is missing. */
    @Nullable
    public Long visionAreaId() {
        return getStep() == null ? null : getStep().visionAreaId();
    }
}
