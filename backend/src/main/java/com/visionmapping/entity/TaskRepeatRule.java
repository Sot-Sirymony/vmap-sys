package com.visionmapping.entity;

import com.visionmapping.entity.enums.RepeatCadence;
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
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V27 SR-FR-09: a task that comes back daily, weekly or monthly. The daily job
 * creates the next task (due on {@code nextDue}) once the current one is due,
 * never in the past and never twice for one date (Q-27-4, BR-27-10).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task_repeat_rules")
public class TaskRepeatRule extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", nullable = false)
    private VisionStep step;

    /** The task the rule was made from; its title, owner, priority and estimate are copied. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_task_id")
    private TaskItem sourceTask;

    /** The most recent task the rule created (or the source task before the first run). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_task_id")
    private TaskItem lastTask;

    @Column(nullable = false, length = 220)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RepeatCadence cadence;

    @Column(name = "next_due", nullable = false)
    private LocalDate nextDue;

    @Column(nullable = false)
    private boolean active;

    /** The date after {@code date} at this cadence. */
    public LocalDate after(LocalDate date) {
        return switch (cadence) {
            case DAILY -> date.plusDays(1);
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
        };
    }
}
