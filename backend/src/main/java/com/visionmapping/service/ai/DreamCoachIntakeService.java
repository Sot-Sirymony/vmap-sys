package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;

import com.visionmapping.dto.request.DreamIntakeRequest;
import com.visionmapping.dto.request.VisionStatementRequest;
import com.visionmapping.dto.response.AiPlanResponses.DreamIntake;
import com.visionmapping.dto.response.AiPlanResponses.VisionStatementDraft;
import com.visionmapping.entity.AppUser;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * V31 v1 step 1 (BRD 31 stages 1 and 2): AI help before a dream exists. It
 * words a vision statement from the participant's own sentence (DC-FR-12),
 * asks up to three questions about a new dream and then restates it from the
 * participant's words only (DC-FR-15, DC-FR-16). Nothing is saved here: the
 * page saves the area and dream through the normal endpoints when the
 * participant confirms (D-31-2 as amended). The target date is the
 * participant's own answer, never Claude's (R-07).
 */
@Service
@RequiredArgsConstructor
public class DreamCoachIntakeService {

    public static final String AREA_FEATURE = "COACH_AREA";
    public static final String DREAM_FEATURE = "COACH_DREAM";
    static final int MAX_QUESTIONS = 3;
    private static final Set<String> FIELDS = Set.of("WHY_IMPORTANT", "SUCCESS_DEFINITION", "TARGET_DATE");

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final AiStructuredRequests requests;
    private final Clock clock;

    /** DC-FR-12: the participant's sentence as a vision statement; their meaning, no new details. */
    public VisionStatementDraft visionStatement(VisionStatementRequest request, String language) {
        AppUser user = lookup.currentUser();
        String apiKey = accessService.requireAvailable(user);
        String records = "Life area: " + clip(request.areaName()) + "\nThe participant's sentence: " + clip(request.sentence());
        String prompt = "Word the participant's sentence as a short vision statement for this life area: one sentence in the "
                + "present tense, as if it is already true. Keep their meaning and their words. Do not add places, numbers, "
                + "roles, people or details they did not give.";
        AiStructuredRequests.Tracked<AiDrafts.VisionStatementText> answer = requests.askTracked(user, apiKey, AREA_FEATURE, language,
                records, prompt, AiDrafts.VisionStatementText.class);
        return new VisionStatementDraft(limit(answer.value().statement(), 300), answer.usage().getId());
    }

    /**
     * DC-FR-15, DC-FR-16: the next question, or the restatement. No question
     * once three are answered or the participant asked to skip them.
     */
    public DreamIntake dreamIntake(DreamIntakeRequest request, String language) {
        AppUser user = lookup.currentUser();
        String apiKey = accessService.requireAvailable(user);
        List<DreamIntakeRequest.Answer> answers = Optional.ofNullable(request.answers()).orElse(List.of());
        boolean mayAsk = !request.suggestAnyway() && answers.size() < MAX_QUESTIONS;
        StringBuilder records = new StringBuilder();
        if (!blank(request.areaName())) {
            records.append("Life area: ").append(clip(request.areaName())).append('\n');
        }
        records.append("The dream in the participant's own words: ").append(clip(request.words())).append('\n');
        for (DreamIntakeRequest.Answer item : answers) {
            records.append("Q: ").append(clip(item.question())).append("\nA: ").append(clip(item.answer())).append('\n');
        }
        String prompt = (mayAsk
                ? "Decide whether this dream is clear enough to plan: what exactly the participant wants, why it matters, what "
                + "success looks like and by when. If one of these is missing, ask the one short, kind question that would "
                + "help most, not one already answered, and set its field. " + (MAX_QUESTIONS - answers.size())
                + " question(s) left at most. Otherwise set done."
                : "Do not ask a question; set done.")
                + " When done, restate the dream as one clear sentence for its title, using only the participant's words and "
                + "answers, and fill why it matters and what success looks like only from what they said. Never invent numbers, "
                + "dates, places, organisations or people; leave a field empty instead.";
        AiStructuredRequests.Tracked<AiDrafts.DreamIntakeDraft> answer = requests.askTracked(user, apiKey, DREAM_FEATURE, language,
                records.toString(), prompt, AiDrafts.DreamIntakeDraft.class);
        AiDrafts.DreamIntakeDraft draft = answer.value();
        Long requestId = answer.usage().getId();
        boolean done = !mayAsk || draft.done() || blank(draft.question());
        if (!done) {
            String field = draft.field() == null ? "" : draft.field().trim().toUpperCase();
            return new DreamIntake(limit(draft.question(), 500), FIELDS.contains(field) ? field : null, false, null, null, null, null,
                    null, requestId);
        }
        String title = Optional.ofNullable(limit(draft.title(), 220)).orElse(limit(request.words(), 220));
        return new DreamIntake(null, null, true, title, limit(request.words(), 3000), limit(draft.whyImportant(), 3000),
                limit(draft.successDefinition(), 3000), answeredDate(answers), requestId);
    }

    /** The participant's own answer to the "by when" question, when it is a date after today. */
    @Nullable
    private LocalDate answeredDate(List<DreamIntakeRequest.Answer> answers) {
        LocalDate today = LocalDate.now(clock);
        for (DreamIntakeRequest.Answer item : answers) {
            if ("TARGET_DATE".equals(item.field())) {
                try {
                    LocalDate date = LocalDate.parse(item.answer().trim());
                    if (date.isAfter(today)) {
                        return date;
                    }
                } catch (DateTimeParseException ignored) {
                    // A written answer such as "next June" is kept in the answers, not turned into a date.
                }
            }
        }
        return null;
    }

    @Nullable
    private static String limit(@Nullable String value, int max) {
        if (blank(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
