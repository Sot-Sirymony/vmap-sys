package com.visionmapping.service.twofactor;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.TrustedDevice;
import com.visionmapping.repository.TrustedDeviceRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.notice.AccountNotices;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V21 (TF-FR-21 to TF-FR-26, D-21-4): browsers a person chose to remember for
 * 30 days, so the second step is skipped there. Only the SHA-256 of the device
 * token is kept, and the first factor is always still needed.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TrustedDeviceService {

    static final int MAX_DEVICES = 10;
    static final int MAX_LABEL = 120;

    private final TrustedDeviceRepository repository;
    private final TwoFactorProperties properties;
    private final SecurityEventService securityEvents;
    private final Clock clock;

    /** TF-FR-22: true when the token is a live device of this account; its last use is updated. */
    public boolean isTrusted(AppUser user, @Nullable String deviceToken) {
        if (deviceToken == null || deviceToken.isBlank() || deviceToken.length() > 100) {
            return false;
        }
        Instant now = clock.instant();
        return repository.findByTokenHash(TwoFactorCodes.sha256(deviceToken.trim()))
                .filter(device -> device.getUser().getId().equals(user.getId()))
                .filter(device -> device.getRemovedAt() == null && device.getExpiresAt().isAfter(now))
                .map(device -> {
                    device.setLastUsedAt(now);
                    return true;
                })
                .orElse(false);
    }

    /** TF-FR-21, TF-FR-26: remembers this browser and returns the token the browser keeps. */
    public String remember(AppUser user, @Nullable String userAgent) {
        Instant now = clock.instant();
        String token = TwoFactorCodes.newToken();
        String label = AccountNotices.describe(userAgent);
        repository.save(TrustedDevice.builder()
                .user(user)
                .tokenHash(TwoFactorCodes.sha256(token))
                .label(label.length() > MAX_LABEL ? label.substring(0, MAX_LABEL) : label)
                .createdAt(now)
                .lastUsedAt(now)
                .expiresAt(now.plus(properties.trustedDeviceLifetime()))
                .build());
        List<TrustedDevice> live = repository.findByUser_IdAndRemovedAtIsNullOrderByLastUsedAtAsc(user.getId());
        for (int i = 0; i < live.size() - MAX_DEVICES; i++) {
            live.get(i).setRemovedAt(now);
        }
        securityEvents.recordFor(user.getId(), SecurityEventType.TRUSTED_DEVICE_ADDED, SecurityOutcome.SUCCESS, label);
        return token;
    }

    /** TF-FR-24: password change or reset, 2FA off, method change or admin reset. */
    public void removeAll(Long userId, String reason) {
        int removed = repository.removeAllForUser(userId, clock.instant());
        if (removed > 0) {
            securityEvents.recordFor(userId, SecurityEventType.TRUSTED_DEVICE_REMOVED, SecurityOutcome.SUCCESS,
                    removed + " removed: " + reason);
        }
    }
}
