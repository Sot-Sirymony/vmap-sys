package com.visionmapping.service.ai;

import com.visionmapping.exception.TooManyRequestsException;
import com.visionmapping.repository.AiSettingsRepository;
import com.visionmapping.repository.PlanBuilderDraftRepository;
import com.visionmapping.entity.AiSettings;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * V11 (PB-NFR-03): one Build my plan session uses at most {@link #limit()} AI
 * requests (an admin setting, {@link #DEFAULT_LIMIT} unless changed), refinements and plan assistant answers included. A session is the
 * participant's live draft for a dream, so while that draft exists every plan
 * AI request about the dream counts, from the guide or from the dream's own
 * plan assistant card. Without a draft nothing is counted. After the limit
 * the guide carries on without AI. The daily limit and monthly cap still apply.
 *
 * <p>Callers count a request after the AI checks pass and before Claude is
 * called, so a request is counted even if Claude then fails. Each count is its
 * own transaction, so it holds inside a read-only one and is never undone.
 */
@Component
@RequiredArgsConstructor
public class PlanSessionGuard {

    public static final int DEFAULT_LIMIT = 15;

    private final PlanBuilderDraftRepository drafts;
    private final AiSettingsRepository settings;
    private final Clock clock;

    /** Counts one request; refuses (429) when the dream's live session has used its limit. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void use(Long userId, Long dreamId) {
        if (!tryUse(userId, dreamId)) {
            throw new TooManyRequestsException(message(limit()));
        }
    }

    /** Counts one request and says true, or says false when the live session has used its limit. True without a session. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryUse(Long userId, Long dreamId) {
        if (drafts.useOneRequest(userId, dreamId, clock.instant(), limit()) > 0) {
            // PB-FR-36: this thread's usage-log row will carry the session's key.
            drafts.findByUser_IdAndDream_Id(userId, dreamId).ifPresent(draft -> {
                if (draft.getSessionKey() == null) {
                    draft.setSessionKey(java.util.UUID.randomUUID().toString());
                }
                PlanSessionContext.set(draft.getSessionKey());
            });
            return true;
        }
        return drafts.findByUser_IdAndDream_Id(userId, dreamId)
                .filter(draft -> draft.getExpiresAt().isAfter(clock.instant()))
                .isEmpty();
    }

    /** The admin's limit per session, or the default when the settings row is missing. */
    public int limit() {
        return settings.findById(AiSettings.SINGLE_ROW_ID).map(AiSettings::getPlanSessionLimit).orElse(DEFAULT_LIMIT);
    }

    static String message(int limit) {
        return "This plan session has used its " + limit + " AI requests. Keep building with the questions at "
                + "each stage, or add your plan to start a new session.";
    }
}
