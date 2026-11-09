package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;
import java.util.Map;

/**
 * V15 (AH-FR-73): how the participant's active partners spread across the four
 * work styles (each the participant's own estimate), the styles that would
 * complement their own (FR-49.4), and any complementary style with no partner
 * yet. Shown only to the participant; partner names never go to Claude.
 */
public record WorkStyleBalanceResponse(String dominant, List<String> complementary, Map<String, Long> counts, long noStyle,
                                       List<String> missing, List<BalancePartner> partners) {

    public WorkStyleBalanceResponse {
        complementary = Copies.list(complementary);
        counts = Copies.map(counts);
        missing = Copies.list(missing);
        partners = Copies.list(partners);
    }

    /** One active partner, for the Work Style page's partner list. */
    public record BalancePartner(Long id, String name, String role, String supportType, String workStyleType, String primaryMotivator) {
    }
}
