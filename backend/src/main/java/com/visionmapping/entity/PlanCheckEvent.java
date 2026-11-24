package com.visionmapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * V31 (DC-FR-64): one rule's result for one run of the Build my plan check, or
 * for one save. Counts only, never text (DC-NFR-04). Ids are plain columns: a
 * deleted account only loses its link (see AccountService).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "plan_check_events")
public class PlanCheckEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "dream_id")
    private Long dreamId;

    @Column(name = "session_key", length = 36)
    private String sessionKey;

    @Column(nullable = false, length = 8)
    private String rule;

    @Column(name = "must_fails", nullable = false)
    private int mustFails;

    @Column(name = "should_fails", nullable = false)
    private int shouldFails;

    @Column(name = "first_run", nullable = false)
    private boolean firstRun;

    @Column(name = "at_save", nullable = false)
    private boolean atSave;

    @Column(nullable = false)
    private boolean accepted;

    @Column(nullable = false)
    private int items;

    @Column(name = "ai_items", nullable = false)
    private int aiItems;

    @Column(name = "ai_fails", nullable = false)
    private int aiFails;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
