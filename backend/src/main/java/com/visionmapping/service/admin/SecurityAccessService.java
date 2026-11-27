package com.visionmapping.service.admin;

import com.visionmapping.dto.request.SecurityAccessUpdateRequest;
import com.visionmapping.dto.response.SecurityAccessResponse;
import com.visionmapping.dto.response.OfferedSignInMethodsResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.SecuritySettings;
import com.visionmapping.entity.enums.IdentityProvider;
import com.visionmapping.entity.enums.SignInOrigin;
import com.visionmapping.entity.enums.TwoFactorScope;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.SignInMethodOffException;
import com.visionmapping.exception.StateConflictException;
import com.visionmapping.repository.AuthSessionRepository;
import com.visionmapping.repository.SecuritySettingsRepository;
import com.visionmapping.security.Permission;
import com.visionmapping.security.PermissionService;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.oauth.GoogleOAuthSettings;
import com.visionmapping.service.stepup.StepUpAction;
import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.RequestRateLimiter;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V24 (BRD 24.0.0): the sign-in methods an Admin allows for everyone else.
 *
 * <p>Read on every sign-in from the database (one row by key), so a change made
 * on any server applies at once (SA-NFR-01). Admins are never affected by the
 * method switches (D-24-5); only Admins change them (D-24-2, gated on
 * "Manage roles", which no other role can hold).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SecurityAccessService {

    private static final String NOT_ALLOWED = "Only an admin can see or change sign-in methods.";
    private static final String ADMIN = UserRole.ADMIN.name();
    /** Fits auth_sessions.revoked_reason (40). */
    static final String SESSION_ENDED_REASON = "sign-in method turned off";

    private final SecuritySettingsRepository repository;
    private final PermissionService permissions;
    private final EntityLookup lookup;
    private final GoogleOAuthSettings googleSettings;
    private final StepUpService stepUpService;
    private final AuthSessionRepository sessions;
    private final SecurityEventService securityEvents;
    private final RequestRateLimiter rateLimiter;
    private final com.visionmapping.service.twofactor.TwoFactorRequirementService twoFactorRequirement;
    private final Clock clock;

    /** The current settings; a missing row (never expected after V92) means today's defaults. */
    @Transactional(readOnly = true)
    public Settings settings() {
        return repository.findById(SecuritySettings.SINGLE_ROW_ID)
                .map(row -> new Settings(row.isPasswordSignIn(), row.isGoogleSignIn(), row.getTwoFactorScope()))
                .orElse(Settings.DEFAULTS);
    }

    /** May this account sign in with this method? Admins always may (D-24-5). */
    @Transactional(readOnly = true)
    public boolean allows(AppUser user, SignInOrigin method) {
        return user.hasRole(UserRole.ADMIN) || settings().allows(method);
    }

    /** SA-FR-10, SA-FR-12: refuses a method that is off for this account (never for Admins, D-24-5). */
    @Transactional(readOnly = true)
    public void requireAllowed(AppUser user, SignInOrigin method) {
        if (!allows(user, method)) {
            throw new SignInMethodOffException(method == SignInOrigin.GOOGLE
                    ? SignInMethodOffException.GOOGLE_OFF : SignInMethodOffException.PASSWORD_OFF);
        }
    }

    /** SA-FR-11, SA-FR-12: a new account is never an Admin, so only the setting counts. */
    @Transactional(readOnly = true)
    public void requireSignUpAllowed(SignInOrigin method) {
        if (!settings().allows(method)) {
            throw new SignInMethodOffException(method == SignInOrigin.GOOGLE
                    ? SignInMethodOffException.GOOGLE_OFF : SignInMethodOffException.PASSWORD_SIGN_UP_OFF);
        }
    }

    /**
     * SA-FR-05 to SA-FR-08. Every refusal comes before the step-up check, so a
     * refused change does not use the code (as in V23).
     */
    public SecurityAccessResponse update(SecurityAccessUpdateRequest request) {
        AppUser actor = permissions.require(lookup.currentUser(), Permission.MANAGE_ROLES, NOT_ALLOWED);
        rateLimiter.check(RequestRateLimiter.Action.USER_ADMIN, String.valueOf(actor.getId()));
        SecuritySettings row = row();
        boolean password = request.passwordSignIn();
        boolean google = request.googleSignIn();
        TwoFactorScope scope = request.twoFactorScope() == null ? row.getTwoFactorScope()
                : TwoFactorScope.valueOf(request.twoFactorScope());
        boolean passwordTurnedOff = row.isPasswordSignIn() && !password;
        boolean googleTurnedOff = row.isGoogleSignIn() && !google;

        if (password == row.isPasswordSignIn() && google == row.isGoogleSignIn() && scope == row.getTwoFactorScope()) {
            throw new BusinessRuleException("Nothing to change.");
        }
        boolean scopeChanged = scope != row.getTwoFactorScope();
        if (scopeChanged && scope == TwoFactorScope.EVERYONE && !twoFactorRequirement.available()) {
            throw new StateConflictException("Two-step sign-in is turned off on this server, so it cannot be required for everyone.");
        }
        guardNobodyIsStranded(password, google, passwordTurnedOff, googleTurnedOff);
        stepUpService.requireFromRequest(StepUpAction.ADMIN_SECURITY_ACCESS);

        String before = describe(row.isPasswordSignIn(), row.isGoogleSignIn(), row.getTwoFactorScope());
        row.setPasswordSignIn(password);
        row.setGoogleSignIn(google);
        row.setTwoFactorScope(scope);
        row.setUpdatedBy(actor);
        repository.saveAndFlush(row);

        int ended = 0;
        if (passwordTurnedOff) {
            ended += sessions.revokeBySignInMethod(SignInOrigin.PASSWORD, ADMIN, SESSION_ENDED_REASON, clock.instant());
        }
        if (googleTurnedOff) {
            ended += sessions.revokeBySignInMethod(SignInOrigin.GOOGLE, ADMIN, SESSION_ENDED_REASON, clock.instant());
        }
        // SA-FR-14, D-24-4: set-up dates given (EVERYONE) or cleared (PRIVILEGED).
        String dates = scopeChanged ? "; " + twoFactorRequirement.scopeChanged(scope)
                + (scope == TwoFactorScope.EVERYONE ? " set-up dates given" : " set-up dates cleared") : "";
        securityEvents.record(SecurityEventType.SECURITY_ACCESS_CHANGED, SecurityOutcome.SUCCESS, "security-settings", null,
                before + " -> " + describe(password, google, scope) + "; " + ended + " sessions ended" + dates
                        + "; reason: " + request.reason().trim());
        return view();
    }

    /** SA-FR-06, D-24-6. */
    private void guardNobodyIsStranded(boolean password, boolean google, boolean passwordTurnedOff, boolean googleTurnedOff) {
        if (!password && !google) {
            throw new BusinessRuleException("At least one sign-in method must stay on.");
        }
        if (!password && !googleAvailable()) {
            throw new StateConflictException("Google sign-in is not set up on this server, so password sign-in must stay on.");
        }
        if (passwordTurnedOff) {
            long stranded = repository.countPasswordOnly(UserStatus.ACTIVE, ADMIN, IdentityProvider.GOOGLE);
            if (stranded > 0) {
                throw new StateConflictException(people(stranded) + " only sign in with a password. Each must link Google "
                        + "from their Account page before password sign-in can be turned off.");
            }
        }
        if (googleTurnedOff) {
            long stranded = repository.countProviderOnly(UserStatus.ACTIVE, ADMIN, IdentityProvider.GOOGLE);
            if (stranded > 0) {
                throw new StateConflictException(people(stranded) + " only sign in with Google. Send each a password reset "
                        + "from the Users page before Google sign-in can be turned off.");
            }
        }
    }

    private static String people(long count) {
        return count == 1 ? "1 person can" : count + " people can";
    }

    private static String describe(boolean password, boolean google, TwoFactorScope scope) {
        return "password " + (password ? "on" : "off") + ", Google " + (google ? "on" : "off") + ", two-step " + scope;
    }

    private SecuritySettings row() {
        return repository.findById(SecuritySettings.SINGLE_ROW_ID)
                .orElseThrow(() -> new IllegalStateException("security_settings has no row; run migration V92"));
    }

    /** SA-FR-03: public, for the sign-in pages. */
    @Transactional(readOnly = true)
    public OfferedSignInMethodsResponse publicMethods() {
        Settings current = settings();
        return new OfferedSignInMethodsResponse(current.passwordSignIn(), current.googleSignIn() && googleAvailable());
    }

    /** SA-FR-02: Admins, and read-only for roles with "View security log". */
    @Transactional(readOnly = true)
    public SecurityAccessResponse view() {
        AppUser actor = permissions.requireAny(lookup.currentUser(), NOT_ALLOWED, Permission.MANAGE_ROLES,
                Permission.VIEW_SECURITY_LOG);
        SecuritySettings row = row();
        UserStatus active = UserStatus.ACTIVE;
        IdentityProvider google = IdentityProvider.GOOGLE;
        var password = new SecurityAccessResponse.MethodUsage(
                repository.countWithPassword(active, ADMIN),
                repository.countPasswordOnly(active, ADMIN, google),
                repository.countOpenSessions(SignInOrigin.PASSWORD, ADMIN, clock.instant()));
        var googleUsage = new SecurityAccessResponse.MethodUsage(
                repository.countLinked(active, ADMIN, google),
                repository.countProviderOnly(active, ADMIN, google),
                repository.countOpenSessions(SignInOrigin.GOOGLE, ADMIN, clock.instant()));
        return new SecurityAccessResponse(row.isPasswordSignIn(), row.isGoogleSignIn(), row.getTwoFactorScope().name(),
                googleAvailable(), permissions.has(actor, Permission.MANAGE_ROLES), row.getUpdatedAt(),
                row.getUpdatedBy() == null ? null : row.getUpdatedBy().getFullName(), password, googleUsage);
    }

    /** GA-FR-02, D-14-5: Google needs its settings and working step-up mail (as GoogleSignInService.enabled()). */
    boolean googleAvailable() {
        return googleSettings.enabled() && stepUpService.active();
    }

    /** The three settings (SA-FR-01). */
    public record Settings(boolean passwordSignIn, boolean googleSignIn, TwoFactorScope twoFactorScope) {

        static final Settings DEFAULTS = new Settings(true, true, TwoFactorScope.PRIVILEGED);

        public boolean allows(SignInOrigin method) {
            return method == SignInOrigin.GOOGLE ? googleSignIn : passwordSignIn;
        }
    }
}
