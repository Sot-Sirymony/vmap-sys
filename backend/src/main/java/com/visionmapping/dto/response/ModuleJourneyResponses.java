package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;

/** V15 Phase 4: which module next (AH-FR-13, AH-FR-14) and how far the participant has come (AH-FR-15). */
public final class ModuleJourneyResponses {

    private ModuleJourneyResponses() {
    }

    /**
     * One recommended module, always unlocked and not completed (AHC-05), or
     * all nulls when every module is done. rule is LOW_CHAIN, PROGRAM_ORDER or
     * ALL_DONE; source is RULE or AI; requestId is set when AI answered.
     */
    public record ModuleNext(Integer moduleNumber, String title, String reason, String rule, String source, Long requestId) {
    }

    public record StageProgress(String stage, int done, int total) {
    }

    /** One barrier's score at the first and the latest assessment. */
    public record ChainChange(String key, String label, int first, int latest) {
    }

    /** AH-FR-15: modules done per stage, chain scores since the first assessment, and the next stage to reach. */
    public record Journey(int done, int total, List<StageProgress> stages, int assessments, List<ChainChange> chains, String nextStage) {
        public Journey {
            stages = Copies.list(stages);
            chains = Copies.list(chains);
        }
    }
}
