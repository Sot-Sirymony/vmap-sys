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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * V31 v1 step 3 (DC-FR-48, D-31-9): how often the participant reviews one
 * dream. weeklyDay is MON to SUN and monthlyDay 1 to 28; empty means no review
 * of that kind. Changed only through {@link #change}.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
@Table(name = "dream_review_rhythms")
public class DreamReviewRhythm extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dream_id")
    private Dream dream;

    @Column(nullable = false)
    private boolean daily;

    @Column(name = "weekly_day", length = 3)
    private String weeklyDay;

    @Column(name = "monthly_day")
    private Integer monthlyDay;

    @Column(nullable = false)
    private boolean quarterly;

    public void change(boolean daily, String weeklyDay, Integer monthlyDay, boolean quarterly) {
        this.daily = daily;
        this.weeklyDay = weeklyDay;
        this.monthlyDay = monthlyDay;
        this.quarterly = quarterly;
    }
}
