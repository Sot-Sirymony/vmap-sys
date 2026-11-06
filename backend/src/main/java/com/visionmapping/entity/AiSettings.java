package com.visionmapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V9 (AI-FR-24): the one row of AI settings. {@code apiKeyEncrypted} holds the
 * Claude API key encrypted with {@code SecretCipher}; it is never sent to the
 * browser, only {@code apiKeyLast4}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_settings")
public class AiSettings extends BaseAuditableEntity {

    public static final long SINGLE_ROW_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 60)
    private String model;

    /** V11 (PB-FR-39): the model for plan building; null uses model. */
    @Column(name = "plan_model", length = 60)
    private String planModel;

    @Column(name = "monthly_budget_usd", nullable = false, precision = 8, scale = 2)
    private BigDecimal monthlyBudgetUsd;

    @Column(name = "api_key_encrypted", length = 1000)
    private String apiKeyEncrypted;

    @Column(name = "api_key_last4", length = 4)
    private String apiKeyLast4;

    @Column(name = "api_key_updated_at")
    private Instant apiKeyUpdatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_key_updated_by")
    private AppUser apiKeyUpdatedBy;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Column(name = "last_test_ok")
    private Boolean lastTestOk;

    @Column(name = "last_test_message", length = 300)
    private String lastTestMessage;

    /** AI-NFR-15: Dream Coach messages one person may send a day. */
    @Column(name = "daily_message_limit", nullable = false)
    private int dailyMessageLimit;

    /** AI-FR-15: add an AI encouragement to reminder emails. Off by default. */
    @Column(name = "ai_reminders", nullable = false)
    private boolean aiReminders;

    /** AI-FR-24: the program's own coaching guidelines, added to the built-in ones. */
    @Column(name = "coaching_guidelines", length = 4000)
    private String coachingGuidelines;

    /** AI-NFR-09: shown with every crisis response. */
    @Column(name = "help_lines", nullable = false, length = 2000)
    private String helpLines;
}
