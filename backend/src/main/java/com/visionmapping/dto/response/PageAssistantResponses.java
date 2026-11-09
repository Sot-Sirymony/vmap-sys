package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.math.BigDecimal;
import java.util.List;

/** V15 Phase 1: what the page assistant endpoints answer. */
public final class PageAssistantResponses {

    private PageAssistantResponses() {
    }

    /** One rule-based problem with the area on screen; found without AI (AH-FR-10). */
    public record PageIssue(String field, String problem, String fix) {
    }

    /** AH-FR-10: the area's rule checks, and whether the assistant may suggest changes for it. */
    public record PageCheck(String page, String area, boolean canPropose, List<PageIssue> issues) {
        public PageCheck {
            issues = Copies.list(issues);
        }
    }

    /** AH-FR-06: one change card. field is a form field of the page; value fills it when applied. */
    public record PageChange(String field, String value, String reason) {
    }

    /** AH-FR-06 to AH-FR-08: checked change cards, the request id to rate them, and what they were based on. */
    public record PageProposals(Long requestId, String note, List<PageChange> changes, List<String> sources) {
        public PageProposals {
            changes = Copies.list(changes);
            sources = Copies.list(sources);
        }
    }

    /** AH-FR-56: one feature's requests, cost, rating and card outcomes over a period. */
    public record PageFeatureMetrics(String feature, long requests, long suggestedItems, long appliedItems, long editedItems,
                                     long dismissedItems, Double appliedRate, long ratings, Double thumbsUpRate,
                                     BigDecimal costUsd, BigDecimal averageCostUsd) {
    }

    /**
     * Phase 1 baselines for AHBO-01, AHBO-03 and AHBO-04, across all participants.
     * Rates are null when there is nothing to divide by.
     */
    public record PageBaselines(long moduleCompletions, long nextModuleWithinSevenDays, Double nextModuleRate,
                                long openObstacles, long openObstaclesWithPlan, Double openObstaclesWithPlanRate,
                                long draftMessages, long sentMessages, long followUpsOverdue) {
    }

    public record PageMetrics(int days, List<PageFeatureMetrics> features, PageBaselines baselines) {
        public PageMetrics {
            features = Copies.list(features);
        }
    }
}
