package com.visionmapping.entity;

import com.visionmapping.entity.enums.EnergyDemand;
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
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task_items")
public class TaskItem extends BaseAuditableEntity {

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
    private WorkStatus status;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent;

    @Column(name = "estimated_hours", precision = 8, scale = 2)
    private BigDecimal estimatedHours;

    @Column(name = "actual_hours", precision = 8, scale = 2)
    private BigDecimal actualHours;

    @Column(name = "blocker_reason", length = 2000)
    private String blockerReason;

    @Column(name = "next_action", length = 2000)
    private String nextAction;

    // FR-34.1: optional energy demand. Nullable — null reads as NEUTRAL (BR-27).
    @Enumerated(EnumType.STRING)
    @Column(name = "energy_demand", length = 16)
    private EnergyDemand energyDemand;

    @Column(name = "completed_at")
    private Instant completedAt;

    // Display order among this step's tasks, for the Vision Map tree's
    // drag-and-drop reorder. 0-based; null on legacy rows until the first
    // reorder (or a fresh create) sets it — callers fall back to id order.
    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(nullable = false)
    private boolean archived;
}
