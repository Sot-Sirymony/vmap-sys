package com.visionmapping.service.ai;

import com.visionmapping.dto.response.AiStatusResponse;
import com.visionmapping.entity.AiConsentEvent;
import com.visionmapping.entity.AiSettings;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.AiConsentAction;
import com.visionmapping.entity.enums.AiOutcome;
import com.visionmapping.exception.AiUnavailableException;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.TooManyRequestsException;
import com.visionmapping.repository.AiConsentEventRepository;
import com.visionmapping.repository.AiUsageLogRepository;
import com.visionmapping.service.AiSettingsService;
import com.visionmapping.service.support.EntityLookup;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V9 (AI-NFR-02, AI-NFR-03, AI-NFR-15): whether AI may run for this person
 * now. Every AI feature calls {@link #requireAvailable} first; the checks run
 * in the order a participant can act on them.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AiAccessService {

    public static final String OK = "OK";
    public static final String OFF = "OFF";
    public static final String BUDGET_REACHED = "BUDGET_REACHED";
    public static final String NO_CONSENT = "NO_CONSENT";
    public static final String DAILY_LIMIT = "DAILY_LIMIT";
    private static final Set<AiOutcome> COUNTED = EnumSet.of(AiOutcome.OK, AiOutcome.CUT_SHORT, AiOutcome.REFUSED);

    private final EntityLookup lookup;
    private final AiSettingsService aiSettingsService;
    private final AiConsentEventRepository consentEventRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AiStatusResponse status() {
        AppUser user = lookup.currentUser();
        AiSettings settings = aiSettingsService.current();
        int left = Math.max(0, settings.getDailyMessageLimit() - (int) sentToday(user));
        return new AiStatusResponse(reason(user, settings) == null, statusReason(user, settings),
                user.getAiConsentAt() != null, user.getAiConsentAt(), settings.getDailyMessageLimit(), left);
    }

    public AiStatusResponse setConsent(boolean consent) {
        AppUser user = lookup.currentUser();
        boolean given = user.getAiConsentAt() != null;
        if (consent != given) {
            user.setAiConsentAt(consent ? Instant.now(clock) : null);
            consentEventRepository.save(AiConsentEvent.builder()
                    .user(user)
                    .action(consent ? AiConsentAction.GIVEN : AiConsentAction.WITHDRAWN)
                    .build());
        }
        return status();
    }

    /** Throws the error the participant should see, or returns the API key to use. */
    @Transactional(readOnly = true)
    public String requireAvailable(AppUser user) {
        AiSettings settings = aiSettingsService.current();
        String reason = reason(user, settings);
        if (reason == null) {
            return aiSettingsService.activeApiKey();
        }
        switch (reason) {
            case NO_CONSENT -> throw new BusinessRuleException("Turn on AI consent in Account settings to use the Dream Coach.");
            case DAILY_LIMIT -> throw new TooManyRequestsException("You have used today's AI requests. They reset tomorrow.");
            default -> throw new AiUnavailableException();
        }
    }

    public BigDecimal spendThisMonth() {
        return usageLogRepository.totalCostSince(monthStart());
    }

    public Instant monthStart() {
        return LocalDate.now(clock).withDayOfMonth(1).atStartOfDay(clock.getZone()).toInstant();
    }

    private String statusReason(AppUser user, AiSettings settings) {
        String reason = reason(user, settings);
        return reason == null ? OK : reason;
    }

    private String reason(AppUser user, AiSettings settings) {
        if (!settings.isEnabled() || aiSettingsService.activeApiKey() == null) {
            return OFF;
        }
        if (spendThisMonth().compareTo(settings.getMonthlyBudgetUsd()) >= 0) {
            return BUDGET_REACHED;
        }
        if (user.getAiConsentAt() == null) {
            return NO_CONSENT;
        }
        if (sentToday(user) >= settings.getDailyMessageLimit()) {
            return DAILY_LIMIT;
        }
        return null;
    }

    /**
     * AI requests that reached Claude today, from any feature: chat, goal and
     * plan builders, plan check. Failed requests and crisis answers (which
     * never call Claude) do not count.
     */
    private long sentToday(AppUser user) {
        Instant dayStart = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        return usageLogRepository.countByUser_IdAndCreatedAtGreaterThanEqualAndOutcomeIn(user.getId(), dayStart, COUNTED);
    }

    /** The API key when AI may run for this person now, empty otherwise. Never throws. */
    @Transactional(readOnly = true)
    public Optional<String> availableKey(AppUser user) {
        return reason(user, aiSettingsService.current()) == null
                ? Optional.ofNullable(aiSettingsService.activeApiKey()) : Optional.empty();
    }
}
