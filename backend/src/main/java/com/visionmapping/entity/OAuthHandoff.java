package com.visionmapping.entity;

import com.visionmapping.entity.enums.OAuthHandoffKind;
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
 * V14 (GA-FR-15, GA-FR-19, GA-FR-30, GA-SEC-07): the one-time code the browser
 * carries back from the Google callback, in the URL fragment. Only a SHA-256 of
 * the code is kept. {@code user} is the account to sign in (SIGN_IN) or the
 * password account to link to (LINK); {@code providerSubject} is the Google
 * {@code sub} the code is bound to.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "oauth_handoffs")
public class OAuthHandoff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OAuthHandoffKind kind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    @Column(name = "remember_me", nullable = false)
    private boolean rememberMe;

    @Column(name = "return_path", nullable = false, length = 512)
    private String returnPath;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
