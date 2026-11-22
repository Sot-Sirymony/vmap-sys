package com.visionmapping.entity;

import com.visionmapping.entity.enums.SignInOrigin;
import com.visionmapping.entity.enums.TwoFactorMethod;
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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V21 (TF-FR-10, TF-FR-11, TF-FR-14, TF-FR-16): the step between the first
 * factor and the second. No session exists until it completes. Only the
 * SHA-256 of the token and the HMAC of an email code are kept.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sign_in_challenges")
public class SignInChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TwoFactorMethod method;

    /** HMAC of the emailed code; null for the authenticator method. */
    @Column(name = "code_hash", length = 64)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "remember_me", nullable = false)
    private boolean rememberMe;

    /** An in-app path only (BRD 14 GA-FR-08). */
    @Column(name = "return_path", nullable = false, length = 512)
    private String returnPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SignInOrigin origin;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
