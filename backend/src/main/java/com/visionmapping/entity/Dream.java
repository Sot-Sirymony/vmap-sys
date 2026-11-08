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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * A meaningful future outcome under one vision area (CLAUDE.md Core Business
 * Rule 1). Holds the coaching answers (why it matters, what success looks like),
 * the decision-gate checklist and the signed commitment. Status and archive flag
 * change only through {@link #changeStatus}, {@link #archive} and {@link #restore}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    // FR-57: an optional intra-area label (BR-46) — a single uppercase
    // letter, never a strict order; ties within a Vision Area are allowed.
    @Column(name = "letter_rank", length = 1)
    private String letterRank;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Setter(AccessLevel.NONE)
    private DreamStatus status;

    // FR-31: aspirational metadata only — never touches progress/completion.
    @Column(nullable = false)
    private boolean moonshot;

    @Column(name = "moonshot_vision", length = 3000)
    private String moonshotVision;

    // FR-56: an optional link to an image representing the dream's
    // fulfilled state (BR-45) — a URL, never an upload; no file storage.
    @Column(name = "image_url", length = 2048)
    private String imageUrl;

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

    // FR-69: the dated commitment that launches this dream. The passion
    // statement it rests on is whyImportant, not a second copy of it.
    @Column(name = "commitment_signed_name", length = 160)
    private String commitmentSignedName;

    @Column(name = "commitment_start_date")
    private LocalDate commitmentStartDate;

    @Column(name = "commitment_first_action", length = 1000)
    private String commitmentFirstAction;

    @Column(name = "committed_at")
    private Instant committedAt;

    // Display order among this vision area's dreams, for drag-and-drop
    // reorder on the Dreams page. 0-based; null on legacy rows until the
    // first reorder (or a fresh create) sets it.
    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private boolean archived;

    /** A status the participant chose. The decision gate is checked by the service. */
    public void changeStatus(DreamStatus newStatus) {
        status = Objects.requireNonNull(newStatus, "status");
    }

    /** Rule 12: hidden, never deleted, and marked Archived. Children are archived by ArchiveCascade. */
    public void archive() {
        status = DreamStatus.ARCHIVED;
        archived = true;
    }

    /**
     * Brings an archived dream back. Its status before the archive is not kept,
     * so it returns as Paused rather than silently Active.
     */
    public void restore() {
        if (!archived) {
            return;
        }
        archived = false;
        if (status == DreamStatus.ARCHIVED) {
            status = DreamStatus.PAUSED;
        }
    }

    /** The id of the vision area it sits in, or null while the link is missing. */
    @Nullable
    public Long visionAreaId() {
        return getVisionArea() == null ? null : getVisionArea().getId();
    }
}
