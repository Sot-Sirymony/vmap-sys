package com.visionmapping.entity;

import com.visionmapping.entity.enums.FearLikelihood;
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
 * FR-65: a fear written down with its worst case, how likely that really is,
 * and a small test that would challenge it. Optionally linked to a Dream.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fear_entries")
public class FearEntry extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 500)
    private String fear;

    @Column(name = "worst_case", nullable = false, length = 2000)
    private String worstCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FearLikelihood likelihood;

    @Column(name = "first_test", nullable = false, length = 2000)
    private String firstTest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_dream_id")
    private Dream relatedDream;

    @Column(nullable = false)
    private boolean archived;
}
