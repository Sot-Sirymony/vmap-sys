package com.visionmapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V14 (GA-FR-06, GA-FR-29, GA-SEC-02): one started Google sign-in. It lives 10
 * minutes and is used once. Only a SHA-256 of {@code state} is kept, and the
 * PKCE {@code codeVerifier} is sealed with SecretCipher, so a copy of this table
 * cannot finish anyone's sign-in.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "oauth_login_attempts")
public class OAuthLoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "state_hash", nullable = false, length = 64)
    private String stateHash;

    /** Sealed with SecretCipher. */
    @Column(name = "code_verifier", nullable = false, length = 512)
    private String codeVerifier;

    @Column(nullable = false, length = 128)
    private String nonce;

    @Column(name = "remember_me", nullable = false)
    private boolean rememberMe;

    /** An in-app path only (GA-FR-08). */
    @Column(name = "return_path", nullable = false, length = 512)
    private String returnPath;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
