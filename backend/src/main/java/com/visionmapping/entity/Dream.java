package com.visionmapping.entity;

import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.DreamType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.ScheduleMode;
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
@Table(name = "dreams")
public class Dream extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vision_area_id", nullable = false)
    private VisionArea visionArea;

    @Column(nullable = false, length = 220)
    private String title;

    @Column(length = 3000)
    private String description;

    @Column(name = "why_important", length = 3000)
    private String whyImportant;

    @Column(name = "success_definition", length = 3000)
    private String successDefinition;

    @Enumerated(EnumType.STRING)
    @Column(name = "dream_type", nullable = false, length = 40)
    private DreamType dreamType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Priority priority;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DreamStatus status;

    // FR-31: aspirational metadata only — never touches progress/completion.
    @Column(nullable = false)
    private boolean moonshot;

    @Column(name = "moonshot_vision", length = 3000)
    private String moonshotVision;

    // FR-51: BOTTOM_UP (default) requires this dream's targetDate to be no
    // earlier than its latest active goal's targetDate (BR-40).
    // TOP_DOWN_FIXED opts out for a real external hard deadline.
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_mode", nullable = false, length = 20)
    private ScheduleMode scheduleMode = ScheduleMode.BOTTOM_UP;

    // FR-55.2: Gate A of BR-44 — an eight-item, originally-worded decision
    // checklist. "Answered" means non-null regardless of value; the answers
    // are diagnostic and never scored (FR-55.4).
    @Column(name = "decision_skipped_research")
    private Boolean decisionSkippedResearch;

    @Column(name = "decision_assumed_no_change")
    private Boolean decisionAssumedNoChange;

    @Column(name = "decision_trusted_unverified_claim")
    private Boolean decisionTrustedUnverifiedClaim;

    @Column(name = "decision_judged_by_appearance")
    private Boolean decisionJudgedByAppearance;

    @Column(name = "decision_under_time_pressure")
    private Boolean decisionUnderTimePressure;

    @Column(name = "decision_no_outside_input")
    private Boolean decisionNoOutsideInput;

    @Column(name = "decision_chased_easy_reward")
    private Boolean decisionChasedEasyReward;

    @Column(name = "decision_dismissed_disagreeing_advice")
    private Boolean decisionDismissedDisagreeingAdvice;

    // FR-55.4: set the first time either BR-44 gate clears for this dream;
    // a non-null value means the gate never re-fires.
    @Column(name = "decision_gate_cleared_at")
    private Instant decisionGateClearedAt;

    @Column(nullable = false)
    private boolean archived;
}
