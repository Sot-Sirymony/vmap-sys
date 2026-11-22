package com.visionmapping.service.twofactor;

import com.visionmapping.dto.response.AccountExportResponse;
import com.visionmapping.dto.response.RecoveryCodesResponse;
import com.visionmapping.dto.response.TotpSetupResponse;
import com.visionmapping.dto.response.TrustedDeviceResponse;
import com.visionmapping.dto.response.TwoFactorStatusResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.TrustedDevice;
import com.visionmapping.entity.TwoFactorRecoveryCode;
import com.visionmapping.entity.UserTwoFactor;
import com.visionmapping.entity.enums.TwoFactorMethod;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.TrustedDeviceRepository;
import com.visionmapping.repository.TwoFactorRecoveryCodeRepository;
import com.visionmapping.repository.UserTwoFactorRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.mail.StepUpMailer;
import com.visionmapping.service.notice.AccountNotice;
import com.visionmapping.service.notice.AccountNotices;
import com.visionmapping.service.stepup.StepUpAction;
import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.util.UserScope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V21 (BRD 21.0.0, TF-FR-01 to TF-FR-09, TF-FR-23): the signed-in person's
 * two-factor settings. Turning it on, changing the method, new recovery codes
 * and turning it off each need a fresh "Verify it's you" code (TF-FR-06), and
 * each sends a notice to the account email (TF-FR-09).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TwoFactorService {

    static final String ISSUER = "Vision Mapping";
    static final Duration SETUP_LIFETIME = Duration.ofMinutes(15);

    private final UserScope userScope;
    private final UserTwoFactorRepository settingsRepository;
    private final TwoFactorRecoveryCodeRepository recoveryRepository;
    private final TrustedDeviceRepository deviceRepository;
    private final TrustedDeviceService trustedDevices;
    private final TotpSecretVault vault;
    private final TwoFactorProperties properties;
    private final StepUpService stepUpService;
    private final StepUpMailer mailer;
    private final AccountNotices accountNotices;
    private final SecurityEventService securityEvents;
    private final Clock clock;

    /** TF-FR-01. */
    @Transactional(readOnly = true)
    public TwoFactorStatusResponse status() {
        AppUser user = userScope.currentUser();
        Instant now = clock.instant();
        Optional<UserTwoFactor> settings = enabledSettings(user.getId());
        List<TrustedDeviceResponse> devices = deviceRepository
                .findByUser_IdAndRemovedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(user.getId(), now).stream()
                .map(TwoFactorService::toResponse)
                .toList();
        return new TwoFactorStatusResponse(
                settings.isPresent(),
                settings.map(found -> found.getMethod().name()).orElse(null),
                settings.map(UserTwoFactor::getEnabledAt).orElse(null),
                settings.isPresent() ? recoveryRepository.countByUser_IdAndUsedAtIsNull(user.getId()) : 0,
                user.getTwoFactorDueAt(),
                emailMethodAllowed(user),
                canTurnOff(user, now),
                devices);
    }

    /**
     * TF-FR-02, TF-FR-03: a new authenticator secret, kept pending; the settings
     * in use (if any) stay as they are until a code confirms it.
     */
    public TotpSetupResponse startAuthenticator(@Nullable String stepUp) {
        stepUpService.require(StepUpAction.TWO_FACTOR_SETTINGS, stepUp);
        AppUser user = userScope.currentUser();
        Instant now = clock.instant();
        String secret = TotpCodes.newSecret();
        UserTwoFactor settings = settingsRepository.findById(user.getId())
                .orElseGet(() -> UserTwoFactor.builder().userId(user.getId()).method(TwoFactorMethod.TOTP).build());
        settings.setPendingTotpSecret(vault.seal(secret));
        settings.setPendingCreatedAt(now);
        settingsRepository.save(settings);
        return new TotpSetupResponse(secret, TotpCodes.otpauthUri(ISSUER, user.getEmail(), secret), now.plus(SETUP_LIFETIME));
    }

    /** TF-FR-03, TF-FR-04: a correct first code turns the authenticator on and gives ten new recovery codes. */
    public RecoveryCodesResponse confirmAuthenticator(String code) {
        AppUser user = userScope.currentUser();
        Instant now = clock.instant();
        UserTwoFactor settings = settingsRepository.findById(user.getId())
                .filter(found -> found.getPendingTotpSecret() != null && found.getPendingCreatedAt() != null)
                .filter(found -> found.getPendingCreatedAt().plus(SETUP_LIFETIME).isAfter(now))
                .orElseThrow(() -> new BusinessRuleException("This setup has expired. Please start again."));
        String secret = vault.open(settings.getPendingTotpSecret())
                .orElseThrow(() -> new BusinessRuleException("This setup has expired. Please start again."));
        OptionalLong step = TotpCodes.matchingStep(secret, code, TotpCodes.step(now), null);
        if (step.isEmpty()) {
            throw new BusinessRuleException("That code is not right. Check the time on your phone and try the newest code.");
        }
        boolean wasOn = settings.isEnabled();
        boolean methodChanged = wasOn && settings.getMethod() != TwoFactorMethod.TOTP;
        settings.setMethod(TwoFactorMethod.TOTP);
        settings.setTotpSecret(settings.getPendingTotpSecret());
        settings.setLastTotpStep(step.getAsLong());
        settings.setPendingTotpSecret(null);
        settings.setPendingCreatedAt(null);
        settings.setEnabledAt(wasOn ? settings.getEnabledAt() : now);
        settingsRepository.save(settings);
        if (wasOn) {
            trustedDevices.removeAll(user.getId(), "two-factor method changed");
        }
        changed(user, wasOn ? SecurityEventType.TWO_FACTOR_METHOD_CHANGED : SecurityEventType.TWO_FACTOR_ENABLED,
                methodChanged ? "switched to an authenticator app" : wasOn ? "set up on a new authenticator app"
                        : "turned on with an authenticator app");
        return new RecoveryCodesResponse(replaceRecoveryCodes(user));
    }

    /**
     * TF-FR-05, D-21-1: participants may use an emailed code. The fresh "Verify
     * it's you" code, sent to the account email and typed back, is the proof the
     * address works, so no second code is sent.
     */
    public RecoveryCodesResponse useEmail(@Nullable String stepUp) {
        AppUser user = userScope.currentUser();
        if (user.getRole() != UserRole.USER) {
            throw new BusinessRuleException("Coach and admin accounts must use an authenticator app.");
        }
        if (mailer.delivery() == StepUpMailer.Delivery.NONE) {
            throw new BusinessRuleException("Email codes cannot be sent right now. Use an authenticator app instead.");
        }
        stepUpService.require(StepUpAction.TWO_FACTOR_SETTINGS, stepUp);
        Instant now = clock.instant();
        UserTwoFactor settings = settingsRepository.findById(user.getId())
                .orElseGet(() -> UserTwoFactor.builder().userId(user.getId()).build());
        boolean wasOn = settings.isEnabled();
        boolean methodChanged = wasOn && settings.getMethod() != TwoFactorMethod.EMAIL;
        settings.setMethod(TwoFactorMethod.EMAIL);
        settings.setTotpSecret(null);
        settings.setLastTotpStep(null);
        settings.setPendingTotpSecret(null);
        settings.setPendingCreatedAt(null);
        settings.setEnabledAt(wasOn ? settings.getEnabledAt() : now);
        settingsRepository.save(settings);
        if (methodChanged) {
            trustedDevices.removeAll(user.getId(), "two-factor method changed");
        }
        changed(user, wasOn ? SecurityEventType.TWO_FACTOR_METHOD_CHANGED : SecurityEventType.TWO_FACTOR_ENABLED,
                wasOn ? "switched to email codes" : "turned on with email codes");
        return new RecoveryCodesResponse(replaceRecoveryCodes(user));
    }

    /** TF-FR-07: ten new recovery codes; every old one stops working at once. */
    public RecoveryCodesResponse newRecoveryCodes(@Nullable String stepUp) {
        AppUser user = userScope.currentUser();
        if (enabledSettings(user.getId()).isEmpty()) {
            throw new BusinessRuleException("Turn on two-factor sign-in first.");
        }
        stepUpService.require(StepUpAction.TWO_FACTOR_SETTINGS, stepUp);
        List<String> codes = replaceRecoveryCodes(user);
        securityEvents.recordFor(user.getId(), SecurityEventType.RECOVERY_CODES_REGENERATED, SecurityOutcome.SUCCESS, null);
        accountNotices.changed(AccountNotice.Kind.TWO_FACTOR_CHANGED, user, null, "given new recovery codes");
        return new RecoveryCodesResponse(codes);
    }

    /** TF-FR-08: removes the secret, recovery codes and trusted devices. Not for an admin or coach past their date. */
    public void turnOff(@Nullable String stepUp) {
        AppUser user = userScope.currentUser();
        if (enabledSettings(user.getId()).isEmpty()) {
            throw new BusinessRuleException("Two-factor sign-in is already off.");
        }
        if (!canTurnOff(user, clock.instant())) {
            throw new BusinessRuleException("Coach and admin accounts must keep two-factor sign-in on.");
        }
        stepUpService.require(StepUpAction.TWO_FACTOR_SETTINGS, stepUp);
        settingsRepository.deleteById(user.getId());
        recoveryRepository.deleteByUserId(user.getId());
        trustedDevices.removeAll(user.getId(), "two-factor turned off");
        changed(user, SecurityEventType.TWO_FACTOR_DISABLED, "turned off");
    }

    /** TF-FR-23: forget one remembered browser of the signed-in person; another person's answers 404. */
    public void removeDevice(Long deviceId) {
        AppUser user = userScope.currentUser();
        TrustedDevice device = deviceRepository.findById(deviceId)
                .filter(found -> found.getUser().getId().equals(user.getId()) && found.getRemovedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Trusted device not found."));
        device.setRemovedAt(clock.instant());
        securityEvents.recordFor(user.getId(), SecurityEventType.TRUSTED_DEVICE_REMOVED, SecurityOutcome.SUCCESS, device.getLabel());
    }

    /** TF-FR-23: forget every remembered browser. */
    public void removeAllDevices() {
        trustedDevices.removeAll(userScope.currentUser().getId(), "removed by the person");
    }

    /** TF-FR-41: what the account export shows; never the secret, a code or a token. */
    @Transactional(readOnly = true)
    public AccountExportResponse.TwoFactor exportFor(Long userId) {
        Optional<UserTwoFactor> settings = enabledSettings(userId);
        List<AccountExportResponse.TrustedDevice> devices = deviceRepository
                .findByUser_IdAndRemovedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(userId, clock.instant()).stream()
                .map(device -> new AccountExportResponse.TrustedDevice(device.getLabel(), device.getCreatedAt(), device.getLastUsedAt()))
                .toList();
        return new AccountExportResponse.TwoFactor(settings.isPresent(), settings.map(found -> found.getMethod().name()).orElse(null),
                settings.map(UserTwoFactor::getEnabledAt).orElse(null),
                settings.isPresent() ? recoveryRepository.countByUser_IdAndUsedAtIsNull(userId) : 0, devices);
    }

    private List<String> replaceRecoveryCodes(AppUser user) {
        recoveryRepository.deleteByUserId(user.getId());
        Instant now = clock.instant();
        List<String> codes = TwoFactorCodes.newRecoveryCodes();
        for (String code : codes) {
            recoveryRepository.save(TwoFactorRecoveryCode.builder()
                    .user(user)
                    .codeHash(properties.hash(TwoFactorCodes.normaliseRecoveryCode(code)))
                    .createdAt(now)
                    .build());
        }
        return codes;
    }

    private void changed(AppUser user, SecurityEventType type, String what) {
        // TF-NFR-06: the role leads the detail, so an admin or coach turning it off raises an alert.
        String role = user.getRole() == UserRole.USER ? "" : user.getRole().name().toLowerCase() + ", ";
        securityEvents.recordFor(user.getId(), type, SecurityOutcome.SUCCESS, role + what);
        accountNotices.changed(AccountNotice.Kind.TWO_FACTOR_CHANGED, user, null, what);
    }

    private Optional<UserTwoFactor> enabledSettings(Long userId) {
        return settingsRepository.findById(userId).filter(UserTwoFactor::isEnabled);
    }

    private boolean emailMethodAllowed(AppUser user) {
        return user.getRole() == UserRole.USER && mailer.delivery() != StepUpMailer.Delivery.NONE;
    }

    /** D-21-2: participants always; admins and coaches only before their set-up-by date. */
    static boolean canTurnOff(AppUser user, Instant now) {
        return user.getRole() == UserRole.USER || (user.getTwoFactorDueAt() != null && user.getTwoFactorDueAt().isAfter(now));
    }

    private static TrustedDeviceResponse toResponse(TrustedDevice device) {
        return new TrustedDeviceResponse(device.getId(), device.getLabel(), device.getCreatedAt(), device.getLastUsedAt(),
                device.getExpiresAt());
    }
}
