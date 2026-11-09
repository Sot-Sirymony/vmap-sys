package com.visionmapping.service.ai;

import com.visionmapping.dto.request.PageAssistantRequest;
import com.visionmapping.dto.request.WorkStyleHelpRequest;
import com.visionmapping.dto.response.WorkStyleBalanceResponse;
import com.visionmapping.dto.response.WorkStyleHelpResponses.StylePractice;
import com.visionmapping.dto.response.WorkStyleHelpResponses.WorkStyleHelpAnswer;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.WorkStyleSupportService;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V15 Phase 5: the AI Work Style helpers (AH-FR-70, AH-FR-71, AH-FR-73,
 * AH-FR-75). They read the Work Style page's own data (the participant's
 * result and pole counts, partner counts by style and support type, plan
 * counts), never partner names or contacts. A partner's style is always the
 * participant's estimate. No helper changes a result, a partner or any plan
 * item; styles are tendencies, and none is better than another (AH-FR-77,
 * AH-FR-79).
 */
@Service
@RequiredArgsConstructor
public class WorkStyleHelpService {

    /** AH-FR-71: the pages a practice may point to. */
    static final Set<String> ACTIONS = Set.of("UNDATED_STEPS", "STEPS", "TASKS", "PARTNERS", "TODAY", "PLAN");
    static final int MAX_PRACTICES = 3;
    static final int MAX_TIPS = 3;
    private static final String TENDENCIES = " Styles are self-reported tendencies, not a measurement or a fixed trait. Never call one "
            + "style better than another, and never rank, judge or exclude a person by style.";

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final AiStructuredRequests requests;
    private final AiPlanFeedbackService feedback;
    private final PageAssistantService pageAssistant;
    private final WorkStyleSupportService support;
    private final VisionStepRepository stepRepository;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public WorkStyleHelpAnswer help(WorkStyleHelpRequest request, String language) {
        AppUser user = lookup.currentUser();
        // Ownership first (AH-NFR-07): another user's partner answers 404 before AI is checked.
        Partner partner = request.partnerId() == null ? null
                : Objects.requireNonNull(transactions.execute(status -> touched(lookup.partner(request.partnerId()))));
        if ("PARTNER".equals(request.mode())) {
            if (partner == null) {
                throw new BusinessRuleException("Choose a partner first.");
            }
            if (partner.getWorkStyleType() == null) {
                throw new BusinessRuleException("Estimate this partner's work style first.");
            }
        }
        if (user.getWorkStyleDominant() == null) {
            throw new BusinessRuleException("Take the work-style quiz first.");
        }
        PageAssistantService.View view = pageAssistant.viewInTransaction(user, new PageAssistantRequest("WORK_STYLE", "result", null, null));
        String apiKey = accessService.requireAvailable(user, "WORK_STYLE");
        String answerIn = " Answer in " + ("km".equals(language) ? "Khmer" : "English") + ".";
        return switch (request.mode()) {
            case "EXPLAIN" -> text(user, apiKey, language, "WORK_STYLE_EXPLAIN", "EXPLAIN", view.records(), view.guard()
                    + "\nExplain the participant's dominant and secondary style in plain words from their own pole counts (for example "
                    + "'you chose fast decisions in 6 of 8 questions, so pace is a clear lean'), the strengths and likely blind spot as "
                    + "tendencies, and one way the style may already show in their plan when the plan counts support it." + TENDENCIES
                    + answerIn);
            case "BALANCE" -> balance(user, apiKey, language, view, answerIn);
            case "TIPS" -> tips(user, apiKey, language, view, answerIn);
            default -> partnerTips(user, apiKey, language, view, partner, request.partnerNotes(), answerIn);
        };
    }

    private static Partner touched(Partner partner) {
        // Read inside the transaction, so the values can be used after it.
        Objects.requireNonNullElse(partner.getRole(), "");
        return partner;
    }

    private WorkStyleHelpAnswer text(AppUser user, String apiKey, String language, String feature, String mode, String records, String task) {
        AiStructuredRequests.Tracked<AiDrafts.StyleTextDraft> answer = requests.askTracked(user, apiKey, feature, language, records, task,
                AiDrafts.StyleTextDraft.class);
        String text = answer.value().text() == null ? "" : clip(answer.value().text().trim(), 1500);
        return new WorkStyleHelpAnswer(feedback.suggested(answer.usage(), 0), mode, text, List.of(), List.of());
    }

    // ---------- AH-FR-73 ----------

    private WorkStyleHelpAnswer balance(AppUser user, String apiKey, String language, PageAssistantService.View view, String answerIn) {
        WorkStyleBalanceResponse balance = support.partnerBalance();
        String records = view.records() + "\nStyles that would complement theirs: " + String.join(", ", balance.complementary())
                + ". Complementary styles none of their partners has yet: "
                + (balance.missing().isEmpty() ? "none" : String.join(", ", balance.missing())) + ".\n";
        return text(user, apiKey, language, "WORK_STYLE_EXPLAIN", "BALANCE", records, view.guard()
                + "\nIn one or two sentences, say why a partner with a missing complementary style could help this participant's plan; "
                + "if none is missing, say how their partners already balance them." + TENDENCIES + answerIn);
    }

    // ---------- AH-FR-71 ----------

    private WorkStyleHelpAnswer tips(AppUser user, String apiKey, String language, PageAssistantService.View view, String answerIn) {
        LocalDate today = LocalDate.now(clock);
        String counts = Objects.requireNonNull(transactions.execute(status -> {
            List<VisionStep> steps = stepRepository.findByUser_IdAndArchivedFalse(user.getId());
            long waiting = steps.stream().filter(step -> step.getStatus() == WorkStatus.WAITING).count();
            long overdue = steps.stream().filter(step -> step.getTargetDate() != null && step.getTargetDate().isBefore(today)
                    && step.getStatus() != WorkStatus.COMPLETED).count();
            long noOwner = steps.stream().filter(step -> step.getOwner() == null || step.getOwner().isBlank()).count();
            return "Plan counts: " + steps.size() + " steps, " + waiting + " waiting, " + overdue + " overdue, " + noOwner + " without an owner.\n";
        }));
        String task = view.guard() + "\nPropose up to three small practices that use this style's strength and cover its likely blind "
                + "spot, based on the plan counts. Tie each to one page: UNDATED_STEPS (steps with no date), STEPS, TASKS, PARTNERS, "
                + "TODAY or PLAN, and give a short habit name." + TENDENCIES + answerIn;
        AiStructuredRequests.Tracked<AiDrafts.StylePracticeDrafts> answer = requests.askTracked(user, apiKey, "WORK_STYLE_TIPS", language,
                view.records() + counts, task, AiDrafts.StylePracticeDrafts.class);
        List<StylePractice> practices = new ArrayList<>();
        for (AiDrafts.StylePracticeDraft draft : Optional.ofNullable(answer.value().practices()).orElse(List.of())) {
            if (draft == null || draft.text() == null || draft.text().isBlank() || practices.size() >= MAX_PRACTICES) {
                continue;
            }
            practices.add(new StylePractice(clip(draft.text().trim(), 400), action(draft.action()),
                    draft.habit() == null ? "" : clip(draft.habit().trim(), 60)));
        }
        return new WorkStyleHelpAnswer(feedback.suggested(answer.usage(), practices.size()), "TIPS", null, List.of(), practices);
    }

    @Nullable
    static String action(@Nullable String value) {
        String upper = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return ACTIONS.contains(upper) ? upper : null;
    }

    // ---------- AH-FR-75 ----------

    private WorkStyleHelpAnswer partnerTips(AppUser user, String apiKey, String language, PageAssistantService.View view, Partner partner,
                                            @Nullable String notes, String answerIn) {
        // Section B: the partner's style estimate, role and support type; never the name or contact details.
        StringBuilder records = new StringBuilder(view.records()).append("\nOne partner (name not shared): the participant estimates their ")
                .append("style as ").append(partner.getWorkStyleType())
                .append(partner.getRole() == null || partner.getRole().isBlank() ? "" : ", role " + clip(partner.getRole().trim(), 100))
                .append(partner.getSupportType() == null ? "" : ", support type " + partner.getSupportType()).append(".\n");
        if (notes != null && !notes.isBlank()) {
            records.append("How the participant says this partner works (for this request only): ").append(clip(notes.trim(), 500)).append('\n');
        }
        String task = view.guard() + "\nGive two or three short tips for working well with this partner that combine both styles, for "
                + "example a Driver asking a Steadier: acknowledge the relationship first and allow time to decide. Say 'you described "
                + "them as' their style; never claim to know a real person's personality." + TENDENCIES + answerIn;
        AiStructuredRequests.Tracked<AiDrafts.StyleTipsDraft> answer = requests.askTracked(user, apiKey, "WORK_STYLE_PARTNER", language,
                records.toString(), task, AiDrafts.StyleTipsDraft.class);
        List<String> tips = Optional.ofNullable(answer.value().tips()).orElse(List.of()).stream()
                .filter(tip -> tip != null && !tip.isBlank()).limit(MAX_TIPS).map(tip -> clip(tip.trim(), 300)).toList();
        return new WorkStyleHelpAnswer(feedback.suggested(answer.usage(), 0), "PARTNER", null, tips, List.of());
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
