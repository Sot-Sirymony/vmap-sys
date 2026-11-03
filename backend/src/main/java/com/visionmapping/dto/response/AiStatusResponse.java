package com.visionmapping.dto.response;

import java.time.Instant;

/**
 * V9: whether the Dream Coach can answer this person now. reason is OK, OFF
 * (switched off or no key), BUDGET_REACHED, NO_CONSENT or DAILY_LIMIT.
 */
public record AiStatusResponse(
        boolean available,
        String reason,
        boolean consentGiven,
        Instant consentAt,
        int dailyLimit,
        int messagesLeftToday) {
}
