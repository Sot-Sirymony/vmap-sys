package com.visionmapping.service.ai;

import com.visionmapping.exception.TooManyRequestsException;
import com.visionmapping.repository.PlanBuilderDraftRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * V11 (PB-NFR-03): one Build my plan session uses at most {@link #LIMIT} AI
 * requests, refinements and plan assistant answers included. A session is the
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

    public static final int LIMIT = 15;
    static final String MESSAGE = "This plan session has used its 15 AI requests. Keep building with the questions at "
            + "each stage, or add your plan to start a new session.";

    private final PlanBuilderDraftRepository drafts;
    private final Clock clock;

    /** Counts one request; refuses (429) when the dream's live session has used its limit. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void use(Long userId, Long dreamId) {
        if (!tryUse(userId, dreamId)) {
            throw new TooManyRequestsException(MESSAGE);
        }
    }

    /** Counts one request and says true, or says false when the live session has used its limit. True without a session. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryUse(Long userId, Long dreamId) {
        if (drafts.useOneRequest(userId, dreamId, clock.instant(), LIMIT) > 0) {
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
}
