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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V11 Phase 2 (PB-FR-30): one participant's unfinished Build my plan session
 * for one dream. content is the draft as JSON (see PlanDraftContent); it is
 * never read as plan data (PB-FR-33) and only its owner can read it (PB-NFR-09).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "plan_builder_drafts")
public class PlanBuilderDraft extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dream_id", nullable = false)
    private Dream dream;

    @Column(nullable = false, length = 20)
    private String stage;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * V11 (PB-NFR-03): AI requests this session has used; the guide stops
     * asking Claude at the limit. Only PlanSessionGuard changes it, in one
     * update, so a draft save never writes an older count back.
     */
    @Builder.Default
    @Column(name = "ai_requests", nullable = false, updatable = false)
    private int aiRequests = 0;

    /** V11 (PB-FR-36): this session's key, carried by every AI request it makes and kept on the finished plan. */
    @Column(name = "session_key", length = 36)
    private String sessionKey;
}
