package com.visionmapping.entity;

import com.visionmapping.entity.enums.GratitudeCategory;
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
 * FR-59.1: a short, freeform record of something the user is grateful for —
 * a gift, a health asset, a person who helped, or anything else. Optionally
 * linked to the Dream or Goal it relates to; never required, never gates
 * anything (BR-48).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gratitude_entries")
public class GratitudeEntry extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GratitudeCategory category;

    @Column(nullable = false, length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_dream_id")
    private Dream relatedDream;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_goal_id")
    private Goal relatedGoal;

    @Column(nullable = false)
    private boolean archived;
}
