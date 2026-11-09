package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;

/** V15 Phase 5: what the Work Style helpers answer. */
public final class WorkStyleHelpResponses {

    private WorkStyleHelpResponses() {
    }

    /**
     * AH-FR-71: one practice. action is one of UNDATED_STEPS, STEPS, TASKS,
     * PARTNERS, TODAY or PLAN, chosen from the program's list; null when Claude
     * named none of them.
     */
    public record StylePractice(String text, String action, String habit) {
    }

    /** One answer: text for EXPLAIN and BALANCE, tips for PARTNER, practices for TIPS. */
    public record WorkStyleHelpAnswer(Long requestId, String mode, String text, List<String> tips, List<StylePractice> practices) {
        public WorkStyleHelpAnswer {
            tips = Copies.list(tips);
            practices = Copies.list(practices);
        }
    }
}
