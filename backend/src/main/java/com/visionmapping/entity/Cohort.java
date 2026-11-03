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
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** V9 (v0.1 FR-01, FR-20): a group of participants with one coach, joined with a code. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cohorts")
public class Cohort extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "join_code", nullable = false, length = 12)
    private String joinCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id")
    private AppUser coach;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Builder.Default
    @Column(nullable = false)
    private boolean archived = false;

    /** V9 (AI-FR-24): AI for this cohort's members. On by default. */
    @Builder.Default
    @Column(name = "ai_enabled", nullable = false)
    private boolean aiEnabled = true;
}
