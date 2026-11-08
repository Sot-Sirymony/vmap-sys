package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.time.LocalDate;
import java.util.List;

/**
 * V9 Phase 6 (AI-FR-12 to AI-FR-17): daily-use suggestions. Like every AI
 * answer, nothing here is saved until the participant chooses to (AI-FR-04).
 */
public final class AiDailyResponses {

    private AiDailyResponses() {
    }

    public record Pick(String itemType, Long itemId, String title, LocalDate dueDate, String reason, Integer minutes) {
    }

    /** AI-FR-12: best first. Empty, with a note, when there is nothing open to pick from. */
    public record NextStep(List<Pick> picks, String note) {
        public NextStep {
            picks = Copies.list(picks);
        }
    
    }

    /** AI-FR-14: fills the weekly review form; the participant edits and saves it. */
    public record WeeklyReviewDraft(String summary, String completedTasks, String delayedTasks, String blockedTasks,
                                    String lessonsLearned, String nextActions) {
    }

    /** AI-FR-16: scores are 1 to 5. */
    public record PitchFeedback(String audience, String reaction, int clarity, int benefit, int ask, String improvedPitch, String tip) {
    }

    /** AI-FR-17: for the participant to copy and send themselves. */
    public record OutreachDraft(String subject, String message) {
    }

    /**
     * AI-FR-13: an idea from the admin-approved list (AI-FR-26) has checkBeforeUse
     * false and may carry its url; any other is to be checked, and never has a link.
     */
    public record ResourceIdea(String kind, String name, String why, boolean checkBeforeUse, String url) {
    }

    public record ResourceIdeas(List<ResourceIdea> resources) {
        public ResourceIdeas {
            resources = Copies.list(resources);
        }
    
    }
}
