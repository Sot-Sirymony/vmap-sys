package com.visionmapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "progress_logs")
public class ProgressLog implements PlanLinked {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "related_task_id", nullable = false)
    private TaskItem relatedTask;

    @Column(name = "progress_percent_before", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercentBefore;

    @Column(name = "progress_percent_after", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercentAfter;

    @Column(length = 2000)
    private String note;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    @Column(nullable = false)
    private boolean archived;
}
