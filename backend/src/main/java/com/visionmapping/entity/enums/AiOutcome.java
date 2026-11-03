package com.visionmapping.entity.enums;

/**
 * AI-NFR-14: how one AI request ended. CRISIS_HANDLED means the safety layer
 * answered with help lines without calling Claude; REFUSED means Claude's
 * safeguards declined.
 */
public enum AiOutcome {
    OK,
    CUT_SHORT,
    REFUSED,
    CRISIS_HANDLED,
    ERROR
}
