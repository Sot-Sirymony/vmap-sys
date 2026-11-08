package com.visionmapping.entity;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.ScheduleMode;
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
 * A specific result needed to reach one dream (Core Business Rule 2). Its
 * progress is the average of its steps (Rule 10) and is set only through
 * {@link #applyRollup}, unless the participant chose a manual override.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "goals")
public class Goal extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dream_id", nullable = false)
    private Dream dream;

    @Column(nullable = false, length = 220)
    private String title;

    @Column(length = 3000)
    private String description;

    @Column(name = "success_criteria", length = 3000)
    private String successCriteria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Priority priority;

    // Same pattern as Dream.letterRank (FR-57): an optional single
    // uppercase letter, a label not a strict order — ties allowed, never
    // gates anything.
    @Column(name = "letter_rank", length = 1)
    private String letterRank;

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

    // FR-14: aspirational metadata only — never touches progress/completion.
    @Column(nullable = false)
    private boolean moonshot;

    @Column(name = "moonshot_vision", length = 3000)
    private String moonshotVision;

    // FR-51: BOTTOM_UP (default) requires this goal's targetDate to be no
    // earlier than its latest active step's targetDate (BR-40).
    // TOP_DOWN_FIXED opts out for a real external hard deadline.
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_mode", nullable = false, length = 20)
    private ScheduleMode scheduleMode = ScheduleMode.BOTTOM_UP;

    // Display order among this dream's goals, for the Vision Map tree's
    // drag-and-drop reorder. 0-based; null on legacy rows until the first
    // reorder (or a fresh create) sets it — callers fall back to id order.
    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private boolean archived;

    /**
     * A status the participant chose. Rules that need other records (a complex
     * step needs tasks, a goal needs its steps done) are checked by the service.
     */
    public void changeStatus(WorkStatus newStatus) {
        status = Objects.requireNonNull(newStatus, "status");
    }

    /**
     * CLAUDE.md Core Business Rules 10 and 11: progress is the average of the
     * unarchived children, and the goal completes once every child is complete.
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

    /** The id of its dream's area, or null while the link is missing. */
    @Nullable
    public Long visionAreaId() {
        return getDream() == null ? null : getDream().visionAreaId();
    }
}
