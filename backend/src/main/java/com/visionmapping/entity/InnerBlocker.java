package com.visionmapping.entity;

import com.visionmapping.entity.enums.InnerBlockerStatus;
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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V34 (D-34-1 to D-34-7): one of the participant's own inner blockers, in the
 * words of their plan guide: the blocker, how it shows up and a short practice.
 * It belongs to the person, not to a step, so it is not an Obstacle (D-17-1).
 * Private like the journals (D-34-11) and never sent to AI (D-34-12).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inner_blockers")
public class InnerBlocker extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 120)
    private String blocker;

    @Column(name = "shows_up", length = 500)
    private String showsUp;

    @Column(nullable = false, length = 300)
    private String practice;

    /** One of the six chain score keys (D-17-3), or null. */
    @Column(name = "chain_key", length = 32)
    private String chainKey;

    /** Null means the blocker applies to all dreams. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dream_id")
    private Dream dream;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habit_id")
    private Habit habit;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InnerBlockerStatus status = InnerBlockerStatus.ACTIVE;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(nullable = false)
    private boolean archived;
}
