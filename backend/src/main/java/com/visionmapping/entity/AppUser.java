package com.visionmapping.entity;

import com.visionmapping.entity.enums.AccentColor;
import com.visionmapping.entity.enums.BackgroundTone;
import com.visionmapping.entity.enums.FontFamily;
import com.visionmapping.entity.enums.InterfaceStyle;
import com.visionmapping.entity.enums.FontSize;
import com.visionmapping.entity.enums.ThemeMode;
import com.visionmapping.entity.enums.ThemePreset;
import com.visionmapping.entity.enums.UiDensity;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.entity.enums.WorkStyleArchetype;
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
import java.time.LocalDate;
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

    /**
     * V23 (D-23-2): the key of the person's role in {@code roles}: a built-in one
     * ({@link UserRole}) or a custom one. What the role allows lives in
     * {@code role_permissions} (PermissionService), never in this name.
     */
    @Column(nullable = false, length = 40)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UserStatus status;

    // FR-77: daily reminders, off until the user turns them on.
    @Column(name = "reminders_enabled", nullable = false)
    private boolean remindersEnabled;

    @Column(name = "reminder_time_zone", length = 60)
    private String reminderTimeZone;

    @Column(name = "last_reminder_on")
    private LocalDate lastReminderOn;

    /** Where reminders go; null sends them to the login email. */
    @Column(name = "reminder_email")
    private String reminderEmail;

    /** V19 (RM-FR-49): the report reminder, off by default. */
    @Enumerated(EnumType.STRING)
    @Column(name = "report_reminder", nullable = false, length = 10)
    @Builder.Default
    private com.visionmapping.entity.enums.ReportReminder reportReminder = com.visionmapping.entity.enums.ReportReminder.NONE;

    /** V19 (RM-FR-49): the weekday of a weekly report reminder, 1 = Monday to 7 = Sunday. */
    @Column(name = "report_reminder_day")
    private Integer reportReminderDay;

    // V9 (D-7): the cohort this person joined with its code, if any.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort;

    /** V11 (PB-FR-16): hours a week for plan work, from the participant's last answer; null until given. */
    @Column(name = "plan_hours_per_week", precision = 5, scale = 1)
    private java.math.BigDecimal planHoursPerWeek;

    // V9 (D-8): fear and attitude journals stay private from the coach until shared.
    @Builder.Default
    @Column(name = "share_journals_with_coach", nullable = false)
    private boolean shareJournalsWithCoach = false;

    // V14 (GA-FR-25, D-14-3): false for an account created with Google, whose
    // password hash is a random secret nobody knows, until a password is set.
    @Builder.Default
    @Column(name = "password_set", nullable = false)
    private boolean passwordSet = true;

    // V21 (TF-FR-32, D-21-2): the set-up-by date for admins and coaches; null
    // when two-factor sign-in is not required.
    @Column(name = "two_factor_due_at")
    private Instant twoFactorDueAt;

    // V23 (UM-FR-19): who created the account (null: self sign-up or Google), and
    // the last role change and block. Plain ids; the security log keeps the detail.
    @Column(name = "created_by_id")
    private Long createdById;

    @Column(name = "role_changed_at")
    private Instant roleChangedAt;

    @Column(name = "role_changed_by_id")
    private Long roleChangedById;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "blocked_by_id")
    private Long blockedById;

    /** V23 (UM-FR-17): when a session last opened for this account. */
    @Column(name = "last_sign_in_at")
    private Instant lastSignInAt;

    // V9 (AI-NFR-03): when this person consented to AI reading their data; null means no consent.
    @Column(name = "ai_consent_at")
    private Instant aiConsentAt;

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
     * FR-42: the typeface. SYSTEM is the platform's own UI face and loads
     * nothing, so this default costs existing users neither a request nor a
     * visible change.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "font_family", nullable = false, length = 20)
    private FontFamily fontFamily = FontFamily.SYSTEM;

    /**
     * FR-40: which surface set the app paints. NEUTRAL is defined as the values
     * that shipped before FR-40, so this defaulting is a no-op for existing users.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "background_tone", nullable = false, length = 20)
    private BackgroundTone backgroundTone = BackgroundTone.NEUTRAL;

    /**
     * FR-48: the shape and weight of the app's surfaces and chrome. CLASSIC is
     * defined as the Fluent treatment that shipped before FR-48, so this default
     * is a no-op for existing users.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "interface_style", nullable = false, length = 20)
    private InterfaceStyle interfaceStyle = InterfaceStyle.CLASSIC;

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

    // FR-49.2: a one-time, retakeable work-style self-assessment. All six
    // fields are nullable together — a user who never takes it has no
    // profile at all, not a half-filled one. Diagnostic metadata only
    // (BR-38): never blocks saving a user, partner, or Ideal Partner Profile.
    @Enumerated(EnumType.STRING)
    @Column(name = "work_style_dominant", length = 20)
    private WorkStyleArchetype workStyleDominant;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_style_secondary", length = 20)
    private WorkStyleArchetype workStyleSecondary;

    @Column(name = "work_style_pace_fast_score")
    private Integer workStylePaceFastScore;

    @Column(name = "work_style_pace_deliberate_score")
    private Integer workStylePaceDeliberateScore;

    @Column(name = "work_style_focus_task_score")
    private Integer workStyleFocusTaskScore;

    @Column(name = "work_style_focus_people_score")
    private Integer workStyleFocusPeopleScore;

    // V15 (AH-FR-76): other AI helpers may use the dominant and secondary style only while this is on.
    @Builder.Default
    @Column(name = "ai_use_work_style", nullable = false)
    private boolean aiUseWorkStyle = false;

    /** V23: set a role by its key (built-in or custom). */
    public void setRole(String roleKey) {
        this.role = roleKey;
    }

    /** V23: set a built-in role. */
    public void setRole(UserRole builtIn) {
        this.role = builtIn.name();
    }

    /** V23: whether the person has this built-in role (custom roles never match). */
    public boolean hasRole(UserRole builtIn) {
        return builtIn.name().equals(role);
    }

    /** V23: lets code and tests name a built-in role while building an account. */
    public static class AppUserBuilder {
        public AppUserBuilder role(UserRole builtIn) {
            this.role = builtIn.name();
            return this;
        }

        public AppUserBuilder role(String roleKey) {
            this.role = roleKey;
            return this;
        }
    }
}
