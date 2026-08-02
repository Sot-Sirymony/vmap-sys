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
 * A single-use permit to set a new password without knowing the old one.
 *
 * The emailed value is not here: {@code tokenHash} is a SHA-256 of it, so this
 * table is useless to anyone who can read it. Nothing in the application can
 * recover the original token from a stored row, which is why a lost link cannot
 * be resent — only replaced.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set on redemption. A spent token is kept so a replayed link can be refused. */
    @Column(name = "used_at")
    private Instant usedAt;

    /**
     * Both conditions matter and neither implies the other: an unexpired token
     * may already have been spent, and an unspent one may have aged out.
     */
    public boolean isRedeemable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
