package com.visionmapping.entity;

import com.visionmapping.entity.enums.TwoFactorMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V21 (BRD 21.0.0, TF-FR-01 to TF-FR-09): an account's two-factor settings.
 * {@code enabledAt} is null while setup is unfinished (TF-FR-03).
 * {@code totpSecret} is the Base32 secret sealed with SecretCipher (TF-NFR-01),
 * and {@code lastTotpStep} is the last 30-second step accepted, so a seen code
 * cannot be used again (TF-FR-13).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "two_factor_settings")
public class UserTwoFactor extends BaseAuditableEntity {

    /** The account's id; the row is the account's, so it has no id of its own. */
    @Id
    @Column(name = "user_id")
    private Long userId;

    /** Read-only view of the account; {@code userId} is what is saved. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TwoFactorMethod method;

    /** Sealed with SecretCipher; null for the email method. */
    @Column(name = "totp_secret", length = 512)
    private String totpSecret;

    @Column(name = "last_totp_step")
    private Long lastTotpStep;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    /** TF-FR-03: an authenticator secret waiting for its first code (sealed); the settings in use stay as they are. */
    @Column(name = "pending_totp_secret", length = 512)
    private String pendingTotpSecret;

    @Column(name = "pending_created_at")
    private Instant pendingCreatedAt;

    /** True once setup was confirmed (TF-FR-03). */
    public boolean isEnabled() {
        return enabledAt != null;
    }
}
