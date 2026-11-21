package com.visionmapping.entity;

import com.visionmapping.entity.enums.IdentityProvider;
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
 * V14 (GA-FR-28): an outside account, such as a Google account, linked to one
 * app account. {@code subject} is the provider's permanent user ID (Google's
 * {@code sub}); the email is recorded but never identifies the account
 * (GA-FR-13). {@code lastEmail} follows Google; the app account's own email
 * never changes because of it (GA-FR-14).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "external_identities")
public class ExternalIdentity extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdentityProvider provider;

    @Column(nullable = false)
    private String subject;

    @Column(name = "email_at_link", nullable = false)
    private String emailAtLink;

    @Column(name = "last_email", nullable = false)
    private String lastEmail;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
