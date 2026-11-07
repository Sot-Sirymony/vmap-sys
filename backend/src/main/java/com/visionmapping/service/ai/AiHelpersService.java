package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;

import com.visionmapping.dto.request.PlanHelpersRequest;
import com.visionmapping.dto.response.AiPlanResponses.HelperSuggestion;
import com.visionmapping.dto.response.AiPlanResponses.HelpersAndRisks;
import com.visionmapping.dto.response.AiPlanResponses.PartnerMatch;
import com.visionmapping.dto.response.AiPlanResponses.RiskSuggestion;
import com.visionmapping.entity.AiApprovedResource;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.FearEntry;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.repository.FearEntryRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V11 Phase 5: who can help and what could get in the way, for Build my plan.
 * Claude names kinds of helpers (roles, never names), likely obstacles and
 * resources (PB-FR-23, PB-FR-24, PB-FR-40). The participant's own partners are
 * matched to each helper here on the server, so partner names and contact
 * details are never sent to Claude (AI-NFR-04, PB-FR-38). Each obstacle shows
 * the kind of partner that fits it (PB-FR-25). The fear journal is used only
 * when the participant asks for it in this request (PB-FR-26).
 */
@Service
@RequiredArgsConstructor
public class AiHelpersService {

    static final int MAX_HELPERS = 5;
    static final int MAX_RISKS = 5;
    static final int MAX_RESOURCES = 6;
    static final int MAX_MATCHES = 3;
    static final int MAX_FEARS = 5;

    /** CLAUDE.md: which kind of partner fits which gap. */
    static final Map<ObstacleType, PartnerSupportType> HELPER_FOR_GAP = Map.of(
            ObstacleType.KNOWLEDGE, PartnerSupportType.MENTOR,
            ObstacleType.SKILL, PartnerSupportType.TECHNICAL,
            ObstacleType.TIME, PartnerSupportType.COLLEAGUE,
            ObstacleType.MONEY, PartnerSupportType.FINANCIAL,
            ObstacleType.MOTIVATION, PartnerSupportType.EMOTIONAL,
            ObstacleType.DECISION, PartnerSupportType.ADVISOR,
            ObstacleType.PARTNER, PartnerSupportType.ADVISOR,
            ObstacleType.SYSTEM, PartnerSupportType.TECHNICAL);
    static final Map<ObstacleType, String> HELPER_LABEL = Map.of(
            ObstacleType.KNOWLEDGE, "Mentor or expert",
            ObstacleType.SKILL, "Technical expert or trainer",
            ObstacleType.TIME, "Someone to delegate to, or an assistant",
            ObstacleType.MONEY, "Financial partner or sponsor",
            ObstacleType.MOTIVATION, "Accountability partner",
            ObstacleType.DECISION, "Advisor",
            ObstacleType.PARTNER, "Advisor",
            ObstacleType.SYSTEM, "Technical expert or trainer");

    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final PlanSessionGuard sessionGuard;
    private final AiStructuredRequests requests;
    private final AiPlanService planService;
    private final AiPlanFeedbackService feedback;
    private final AiResourceIdeas resourceIdeas;
    private final PartnerRepository partnerRepository;
    private final FearEntryRepository fearEntryRepository;
    private final TransactionTemplate transactions;

    public HelpersAndRisks suggest(Long dreamId, PlanHelpersRequest request, String language) {
        AppUser user = lookup.currentUser();
        lookup.dream(dreamId);
        String apiKey = accessService.requireAvailable(user);
        sessionGuard.use(user.getId(), dreamId); // PB-NFR-03
        List<PlanHelpersRequest.Item> items = Optional.ofNullable(request.items()).orElse(List.of());
        Set<String> keys = items.stream().map(PlanHelpersRequest.Item::key).collect(Collectors.toSet());
        Map<Long, AiApprovedResource> approved = resourceIdeas.approved();
        String records = transactions.execute(status -> {
            Dream dream = lookup.dream(dreamId);
            StringBuilder out = new StringBuilder(planService.describeDream(user, dream));
            if (!items.isEmpty()) {
                out.append("\nThe plan being built, with keys in brackets:\n");
                for (PlanHelpersRequest.Item item : items) {
                    out.append("GOAL".equals(item.kind()) ? "Goal [" : "  Step [").append(clip(item.key())).append("] ")
                            .append(clip(item.title()));
                    if (item.resources() != null && !item.resources().isBlank()) {
                        out.append(" | needs: ").append(clip(item.resources()));
                    }
                    out.append('\n');
                }
            }
            if (request.useFears()) {
                List<FearEntry> fears = fearEntryRepository.findByUser_IdAndArchivedFalse(user.getId()).stream()
                        .filter(fear -> fear.getRelatedDream() == null || fear.getRelatedDream().getId().equals(dream.getId()))
                        .limit(MAX_FEARS).toList();
                if (!fears.isEmpty()) {
                    out.append("\nFears from the participant's fear journal, shared for this plan:\n");
                    fears.forEach(fear -> out.append("- ").append(clip(fear.getFear()))
                            .append(fear.getFirstTest() == null ? "" : " | first test: " + clip(fear.getFirstTest())).append('\n'));
                }
            }
            out.append(AiResourceIdeas.describe(approved));
            return out.toString();
        }) + AiPlanService.askRecords(request.ask());
        String prompt = "Look at this plan as a careful coach. Name up to " + MAX_HELPERS + " kinds of helpers it needs, as roles "
                + "with a support type, never a person's name. Name up to " + MAX_RISKS + " likely obstacles, each with a type, "
                + "a severity and a practical first way to handle it. Point each at the goal or step it belongs to, using the "
                + "keys in brackets." + (request.useFears()
                ? " Where a fear from the journal is a real risk to this plan, include it and mark it fromFears."
                : " Set fromFears to false.") + " " + AiResourceIdeas.instruction(MAX_RESOURCES) + " Do not repeat helpers or "
                + "obstacles the plan already covers. Follow the participant's note when there is one.";
        AiStructuredRequests.Tracked<AiDrafts.HelpersRisksDraft> answer = requests.askTracked(user, apiKey, "HELPERS_RISKS",
                language, records, prompt, AiDrafts.HelpersRisksDraft.class);
        AiDrafts.HelpersRisksDraft draft = answer.value();

        List<Partner> partners = partnerRepository.findByUser_IdAndArchivedFalse(user.getId()).stream()
                .filter(partner -> partner.getStatus() != PartnerStatus.DECLINED).toList();
        List<HelperSuggestion> helpers = new ArrayList<>();
        for (AiDrafts.HelperDraft helper : Optional.ofNullable(draft.helpers()).orElse(List.of())) {
            if (blank(helper.role()) || helpers.size() >= MAX_HELPERS) {
                continue;
            }
            PartnerSupportType type = supportType(helper.supportType());
            String role = limit(helper.role(), 120);
            helpers.add(new HelperSuggestion(role, type.name(), knownKey(helper.forKey(), keys), limit(helper.reason(), 300),
                    matches(partners, type, role)));
        }
        List<RiskSuggestion> risks = new ArrayList<>();
        for (AiDrafts.RiskDraft risk : Optional.ofNullable(draft.risks()).orElse(List.of())) {
            if (blank(risk.title()) || risks.size() >= MAX_RISKS) {
                continue;
            }
            ObstacleType type = obstacleType(risk.obstacleType());
            PartnerSupportType helper = HELPER_FOR_GAP.getOrDefault(type, PartnerSupportType.OTHER);
            risks.add(new RiskSuggestion(limit(risk.title(), 220), limit(risk.description(), 3000), type.name(),
                    severity(risk.severity()), limit(risk.solution(), 3000), knownKey(risk.forKey(), keys), helper.name(),
                    HELPER_LABEL.get(type), request.useFears() && risk.fromFears()));
        }
        var resources = AiResourceIdeas.toIdeas(draft.resources(), approved, MAX_RESOURCES);
        return new HelpersAndRisks(helpers, risks, resources, feedback.suggested(answer.usage(), helpers.size() + risks.size()));
    }

    /**
     * PB-FR-23: the participant's partners who may fit, best first: the same
     * support type counts most, then words the roles share. Only their own
     * partners, never sent to Claude.
     */
    static List<PartnerMatch> matches(List<Partner> partners, PartnerSupportType type, String role) {
        Set<String> words = words(role);
        return partners.stream()
                .map(partner -> Map.entry(partner, (partner.getSupportType() == type && type != PartnerSupportType.OTHER ? 2 : 0)
                        + (int) words(partner.getRole()).stream().filter(words::contains).count()))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.comparing((Map.Entry<Partner, Integer> entry) -> entry.getValue()).reversed()
                        .thenComparing(entry -> entry.getKey().getName(), String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_MATCHES)
                .map(entry -> new PartnerMatch(entry.getKey().getId(), entry.getKey().getName(), entry.getKey().getRole(),
                        entry.getKey().getSupportType() == null ? null : entry.getKey().getSupportType().name(),
                        entry.getKey().getStatus().name()))
                .toList();
    }

    private static Set<String> words(String text) {
        if (text == null) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() >= 4).collect(Collectors.toSet());
    }

    private static String knownKey(String key, Set<String> keys) {
        return key == null || !keys.contains(key.trim()) ? null : key.trim();
    }

    static PartnerSupportType supportType(String value) {
        try {
            return value == null ? PartnerSupportType.OTHER : PartnerSupportType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return PartnerSupportType.OTHER;
        }
    }

    static ObstacleType obstacleType(String value) {
        try {
            return value == null ? ObstacleType.OTHER : ObstacleType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ObstacleType.OTHER;
        }
    }

    private static String severity(String value) {
        String upper = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return SEVERITIES.contains(upper) ? upper : "MEDIUM";
    }

    private static String limit(String value, int max) {
        if (blank(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 3) + "...";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
