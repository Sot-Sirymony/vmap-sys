package com.visionmapping.entity;

import com.visionmapping.entity.enums.CommunicationStatus;
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
@Table(name = "communication_messages")
public class CommunicationMessage extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_dream_id")
    private Dream relatedDream;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_goal_id")
    private Goal relatedGoal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_task_id")
    private TaskItem relatedTask;

    @Column(length = 180)
    private String audience;

    @Column(length = 500)
    private String purpose;

    @Column(length = 220)
    private String subject;

    @Column(length = 1000)
    private String hook;

    @Column(length = 2000)
    private String problem;

    @Column(length = 2000)
    private String request;

    @Column(name = "benefit_to_partner", length = 2000)
    private String benefitToPartner;

    @Column(name = "word_picture", length = 2000)
    private String wordPicture;

    @Column(name = "expected_outcome", length = 2000)
    private String expectedOutcome;

    // FR-52.1: four additive persuasion fields, all optional (FR-52.3 — a
    // message using none of them generates exactly as it did before FR-52).
    @Column(name = "objections_and_answers", length = 2000)
    private String objectionsAndAnswers;

    @Column(name = "social_proof", length = 2000)
    private String socialProof;

    @Column(name = "value_comparison", length = 2000)
    private String valueComparison;

    @Column(name = "call_to_action", length = 2000)
    private String callToAction;

    @Column(name = "message_body", length = 6000)
    private String messageBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CommunicationStatus status;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(nullable = false)
    private boolean archived;
}
