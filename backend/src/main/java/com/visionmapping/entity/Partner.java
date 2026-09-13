package com.visionmapping.entity;

import com.visionmapping.entity.enums.OfferType;
import com.visionmapping.entity.enums.PartnerMotivator;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.entity.enums.WorkStyleArchetype;
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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "partners")
public class Partner extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 120)
    private String role;

    @Column(length = 180)
    private String organization;

    @Column(length = 180)
    private String email;

    @Column(length = 60)
    private String phone;

    @Column(length = 120)
    private String strength;

    @Enumerated(EnumType.STRING)
    @Column(name = "support_type", nullable = false, length = 40)
    private PartnerSupportType supportType;

    // FR-15.2: the exchange basis this partner responds to. Optional.
    @Enumerated(EnumType.STRING)
    @Column(name = "offer_type", length = 40)
    private OfferType offerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_vision_area_id")
    private VisionArea relatedVisionArea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_dream_id")
    private Dream relatedDream;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_goal_id")
    private Goal relatedGoal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_step_id")
    private VisionStep relatedStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_task_id")
    private TaskItem relatedTask;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PartnerStatus status;

    @Column(length = 3000)
    private String notes;

    // FR-50.1: seven original, plain-language integrity-and-reliability
    // checks. Gate only fires the first time a FINANCIAL/TECHNICAL partner
    // moves to ACTIVE (BR-39); otherwise diagnostic/audit-trail metadata.
    @Column(name = "flag_dishonesty")
    private Boolean flagDishonesty;

    @Column(name = "flag_anger")
    private Boolean flagAnger;

    @Column(name = "flag_poor_judgment")
    private Boolean flagPoorJudgment;

    @Column(name = "flag_outsized_reward")
    private Boolean flagOutsizedReward;

    @Column(name = "flag_flattery_pressure")
    private Boolean flagFlatteryPressure;

    @Column(name = "flag_gossip")
    private Boolean flagGossip;

    @Column(name = "flag_disregard_boundaries")
    private Boolean flagDisregardBoundaries;

    // FR-50.3: required only when at least one flag above is set, and only
    // for the transition BR-39 gates.
    @Column(name = "risk_override_note", length = 1000)
    private String riskOverrideNote;

    // FR-50.4: what drives the partner, distinct from offerType (what the
    // user offers them).
    @Enumerated(EnumType.STRING)
    @Column(name = "primary_motivator", length = 20)
    private PartnerMotivator primaryMotivator;

    // FR-50.2: set the first time the BR-39 gate is cleared for this
    // partner; a non-null value means the gate never re-fires.
    @Column(name = "vetted_at")
    private Instant vettedAt;

    // FR-49.3: the user's own estimate of the partner's work style — there is
    // no partner login, so this is never a self-report. Purely descriptive;
    // never blocks saving (BR-38).
    @Enumerated(EnumType.STRING)
    @Column(name = "work_style_type", length = 20)
    private WorkStyleArchetype workStyleType;

    @Column(nullable = false)
    private boolean archived;
}
