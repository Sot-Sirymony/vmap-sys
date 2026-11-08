package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;

/**
 * V9 Phase 9 (AI-FR-22, AI-FR-23): drafts for an admin to review. Nothing here
 * is published; the admin edits and saves through the normal editors.
 */
public final class AiAdminResponses {

    private AiAdminResponses() {
    }

    public record QuizItem(String question, String answer) {
    }

    public record ModuleContentDraft(String title, String summary, String guidance, String exerciseInstructions,
                                     String examples, List<QuizItem> quiz) {
        public ModuleContentDraft {
            quiz = Copies.list(quiz);
        }
    
    }

    /** One per input text, same order; null where a draft was dropped (for example a placeholder went missing). */
    public record Translations(List<String> translations) {
        public Translations {
            translations = Copies.list(translations);
        }
    
    }
}
