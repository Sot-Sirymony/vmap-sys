package com.visionmapping.entity;

import com.visionmapping.entity.enums.AccentColor;
import com.visionmapping.entity.enums.BackgroundTone;
import com.visionmapping.entity.enums.FontSize;
import com.visionmapping.entity.enums.ThemeMode;
import com.visionmapping.entity.enums.ThemePreset;
import com.visionmapping.entity.enums.UiDensity;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "app_users")
public class AppUser extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UserStatus status;

    // FR-39.6: appearance preferences live on the account so a chosen look
    // follows the user across browsers and devices. Every field is non-null
    // with the same default the frontend has used since FR-18, so a user who
    // never opens the Appearance settings behaves exactly as before. The
    // @Builder.Default values matter: AuthService creates users through the
    // builder, and without them a new user would be saved with nulls against
    // NOT NULL columns.

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "theme_preset", nullable = false, length = 40)
    private ThemePreset themePreset = ThemePreset.FLUENT_SYSTEM;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, length = 20)
    private ThemeMode themeMode = ThemeMode.SYSTEM;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "theme_accent", nullable = false, length = 20)
    private AccentColor themeAccent = AccentColor.BLUE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "ui_density", nullable = false, length = 20)
    private UiDensity uiDensity = UiDensity.COMFORTABLE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "font_size", nullable = false, length = 20)
    private FontSize fontSize = FontSize.MEDIUM;

    /**
     * FR-40: which surface set the app paints. NEUTRAL is defined as the values
     * that shipped before FR-40, so this defaulting is a no-op for existing users.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "background_tone", nullable = false, length = 20)
    private BackgroundTone backgroundTone = BackgroundTone.NEUTRAL;

    /** FR-39.3: composes with light and dark rather than replacing either. */
    @Builder.Default
    @Column(name = "high_contrast", nullable = false)
    private boolean highContrast = false;

    /**
     * FR-39.4: asks for less motion than the OS preference, never more — the OS
     * {@code prefers-reduced-motion} setting still applies on its own (BR-19).
     */
    @Builder.Default
    @Column(name = "reduce_motion", nullable = false)
    private boolean reduceMotion = false;
}
