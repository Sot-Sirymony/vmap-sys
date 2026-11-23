package com.visionmapping.entity;

import com.visionmapping.entity.enums.TwoFactorScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V24 (BRD 24.0.0, SA-FR-01): the one row of sign-in method settings. Seeded by
 * V92 with today's behaviour. Admins are never affected by the method switches
 * (D-24-5).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "security_settings")
public class SecuritySettings extends BaseAuditableEntity {

    public static final long SINGLE_ROW_ID = 1L;

    @Id
    private Long id;

    @Column(name = "password_sign_in", nullable = false)
    private boolean passwordSignIn;

    @Column(name = "google_sign_in", nullable = false)
    private boolean googleSignIn;

    @Enumerated(EnumType.STRING)
    @Column(name = "two_factor_scope", nullable = false, length = 20)
    private TwoFactorScope twoFactorScope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private AppUser updatedBy;
}
