package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;
import java.util.Map;

/**
 * V9 Phase 7: AI help inside the module exercises (AIB-01 to AIB-15) and the
 * assessment explanation (v0.1 FR-02 change). Drafts only: an exercise counts,
 * and unlocks the next module, only when the participant saves it (AC-09).
 */
public final class AiModuleResponses {

    private AiModuleResponses() {
    }

    /** mode START fills questions and example; DRAFT fills rows; FEEDBACK fills feedback and improvements. */
    /**
     * V15 Phase 4 adds explanation and whyItMatters (EXPLAIN, AH-FR-18) and
     * checks (CHECK, AH-FR-20). Only the parts of the mode are set.
     */
    public record ModuleHelp(String mode, List<String> questions, String example, List<Map<String, String>> rows,
                             String feedback, List<String> improvements, String explanation, String whyItMatters,
                             List<ModuleCheck> checks) {
        public ModuleHelp {
            questions = Copies.list(questions);
            rows = Copies.list(rows);
            improvements = Copies.list(improvements);
            checks = Copies.list(checks);
        }

        /** The V9 shape, with no explanation or checks. */
        public ModuleHelp(String mode, List<String> questions, String example, List<Map<String, String>> rows,
                          String feedback, List<String> improvements) {
            this(mode, questions, example, rows, feedback, improvements, null, null, List.of());
        }
    
    }

    /** focusModule and focusTitle are null when Claude named no module that exists. */
    public record AssessmentExplanation(String explanation, Integer focusModule, String focusTitle, String reason,
                                        List<ChainNote> lowChains, String trend) {
        public AssessmentExplanation {
            lowChains = Copies.list(lowChains);
        }
    }

    /** V15 (AH-FR-25): one sentence on a barrier scored 1 or 2. */
    public record ChainNote(String key, String label, int score, String note) {
    }

    /** V15 (AH-FR-26): one practice for a barrier scored 3 or less. The app action comes from the program, not from Claude. */
    public record ChainPractice(String chainKey, String label, int score, String practice, String habit) {
    }

    public record AssessmentPractice(Long requestId, List<ChainPractice> practices) {
        public AssessmentPractice {
            practices = Copies.list(practices);
        }
    }

    /** V15 (AH-FR-29): a comparison with the previous take, and the modules completed in between. */
    public record WhatChanged(Long requestId, String summary, List<String> modulesBetween) {
        public WhatChanged {
            modulesBetween = Copies.list(modulesBetween);
        }
    }

    /** V15 (AH-FR-20): one field left empty or too short, with a one-line tip. row counts from 0. */
    public record ModuleCheck(int row, String field, String tip) {
    }
}
