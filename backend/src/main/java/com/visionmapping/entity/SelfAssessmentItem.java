package com.visionmapping.entity;

import com.visionmapping.entity.enums.GapCoverMethod;
import com.visionmapping.entity.enums.Relevance;
import com.visionmapping.entity.enums.SelfAssessmentSource;
import com.visionmapping.entity.enums.StrengthKind;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FR-79 to FR-83: one strength or weakness, with a real example as evidence,
 * judged against one dream. An optional outside view records what someone
 * else said. A high-relevance weakness carries the one way it is covered and
 * the record that way created, so removing the gap can offer to remove it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "self_assessment_items")
public class SelfAssessmentItem extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dream_id", nullable = false)
    private Dream dream;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StrengthKind kind;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, length = 2000)
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Relevance relevance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SelfAssessmentSource source;

    @Column(name = "outside_by", length = 160)
    private String outsideBy;

    @Column(name = "outside_view", length = 2000)
    private String outsideView;

    /** Null when there is no outside view; false flags a mismatch (FR-83). */
    @Column(name = "outside_agrees")
    private Boolean outsideAgrees;

    @Enumerated(EnumType.STRING)
    @Column(name = "cover_method", length = 20)
    private GapCoverMethod coverMethod;

    @Column(name = "cover_note", length = 1000)
    private String coverNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_partner_id")
    private Partner linkedPartner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_step_id")
    private VisionStep linkedStep;

    /** 6 for the resource gap list, 7 for the leverage map. */
    @Column(name = "linked_module_number")
    private Integer linkedModuleNumber;

    /** The exercise row as it was added, so the same row can be found again. */
    @Column(name = "linked_module_row", length = 4000)
    private String linkedModuleRow;

    @Column(nullable = false)
    private boolean archived;

    public boolean isHighWeakness() {
        return kind == StrengthKind.WEAKNESS && relevance == Relevance.HIGH;
    }

    public void clearCover() {
        coverMethod = null;
        coverNote = null;
        linkedPartner = null;
        linkedStep = null;
        linkedModuleNumber = null;
        linkedModuleRow = null;
    }
}
