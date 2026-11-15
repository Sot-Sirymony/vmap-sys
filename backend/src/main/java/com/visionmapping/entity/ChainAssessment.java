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

/**
 * FR-71: how much each of the six chains holds the user back. 5 means barely.
 * V17 (BL-FR-30, D-17-3): the six are settling, fear, criticism, priorities,
 * persistence and vision. Knowledge and resources are retired (they are step
 * gaps now): kept on past rows, null on new ones (BL-FR-31).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chain_assessments")
public class ChainAssessment extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "taken_on", nullable = false)
    private LocalDate takenOn;

    @Column(name = "settling_score", nullable = false)
    private Integer settlingScore;

    @Column(name = "fear_score", nullable = false)
    private Integer fearScore;

    @Column(name = "criticism_score", nullable = false)
    private Integer criticismScore;

    @Column(name = "vision_score", nullable = false)
    private Integer visionScore;

    /** Retired in V17: null on assessments taken after it. */
    @Column(name = "knowledge_score")
    private Integer knowledgeScore;

    /** Retired in V17: null on assessments taken after it. */
    @Column(name = "resources_score")
    private Integer resourcesScore;

    /** V17 BL-FR-30: misplaced priorities. Null on assessments taken before it. */
    @Column(name = "priorities_score")
    private Integer prioritiesScore;

    /** V17 BL-FR-30: lack of persistence. Null on assessments taken before it. */
    @Column(name = "persistence_score")
    private Integer persistenceScore;
}
