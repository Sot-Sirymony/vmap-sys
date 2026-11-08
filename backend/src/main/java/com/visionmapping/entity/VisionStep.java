package com.visionmapping.entity;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
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
 * One stage of work toward a goal (Core Business Rule 3), called VisionStep to
 * avoid confusion with generic "step" names. A complex step should be broken into
 * tasks (Rule 7). Its progress is the average of its tasks (Rule 10), set only
 * through {@link #applyRollup}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "vision_steps")
public class VisionStep extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @Column(nullable = false, length = 220)
    private String title;

    @Column(length = 3000)
    private String description;

    // FR-64: who is responsible for this step.
    @Column(nullable = false, length = 160)
    private String owner;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "is_complex", nullable = false)
    private boolean complex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Priority priority;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Setter(AccessLevel.NONE)
    private WorkStatus status;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    @Setter(AccessLevel.NONE)
    private BigDecimal progressPercent;

    @Column(name = "manual_progress_override", nullable = false)
    private boolean manualProgressOverride;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private boolean archived;

    /** v0.1 FR-05: the resources and people this step needs. */
    @Column(length = 1000)
    private String resources;

    /** V11 (PB-FR-12): estimated effort in hours; optional. */
    @Column(name = "estimated_hours", precision = 6, scale = 2)
    private BigDecimal estimatedHours;

    /** V11 (PB-FR-12): the step of the same goal this one starts after, when order matters; optional. */
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "starts_after_step_id")
    private VisionStep startsAfter;

    /** AI-FR-11: how often this step was given a new date while overdue. */
    @Builder.Default
    @Column(name = "overdue_reschedules", nullable = false)
    private int overdueReschedules = 0;

    /**
     * A status the participant chose. Rules that need other records (a complex
     * step needs tasks, a goal needs its steps done) are checked by the service.
     */
    public void changeStatus(WorkStatus newStatus) {
        status = Objects.requireNonNull(newStatus, "status");
    }

    /**
     * CLAUDE.md Core Business Rules 10 and 11: progress is the average of the
     * unarchived children, and the step completes once every child is complete.
     */
    public void applyRollup(BigDecimal averageProgress, boolean allChildrenComplete) {
        progressPercent = Objects.requireNonNull(averageProgress, "averageProgress");
        if (allChildrenComplete) {
            status = WorkStatus.COMPLETED;
        }
    }

    /** Rule 12: hidden, never deleted. Children are archived by ArchiveCascade. */
    public void archive() {
        archived = true;
    }

    public void restore() {
        archived = false;
    }

    /** The id of its goal's area, or null while the link is missing. */
    @Nullable
    public Long visionAreaId() {
        return getGoal() == null ? null : getGoal().visionAreaId();
    }
}
