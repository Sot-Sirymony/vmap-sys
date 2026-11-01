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

/** FR-71: how much each of the six chains holds the user back. 5 means barely. */
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

    @Column(name = "knowledge_score", nullable = false)
    private Integer knowledgeScore;

    @Column(name = "resources_score", nullable = false)
    private Integer resourcesScore;
}
