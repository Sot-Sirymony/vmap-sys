package com.visionmapping.dto.response;

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
    public record ModuleHelp(String mode, List<String> questions, String example, List<Map<String, String>> rows,
                             String feedback, List<String> improvements) {
    }

    /** focusModule and focusTitle are null when Claude named no module that exists. */
    public record AssessmentExplanation(String explanation, Integer focusModule, String focusTitle, String reason) {
    }
}
