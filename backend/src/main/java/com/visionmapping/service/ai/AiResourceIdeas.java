package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;

import com.visionmapping.dto.response.AiDailyResponses.ResourceIdea;
import com.visionmapping.entity.AiApprovedResource;
import com.visionmapping.repository.AiApprovedResourceRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI-FR-13 and AI-FR-26, shared by resource ideas and (V11, PB-FR-40) the Build
 * my plan helpers stage: the admin-approved resources go to Claude first, with
 * ids; an idea that names one comes back with the approved name, kind and link.
 * Anything else is marked "check before use" and carries no link.
 */
@Component
@RequiredArgsConstructor
public class AiResourceIdeas {

    static final int MAX_APPROVED = 60;
    private static final Set<String> RESOURCE_KINDS = Set.of("COURSE", "BOOK", "TOOL", "ORGANISATION", "FUNDING", "PERSON", "OTHER");

    private final AiApprovedResourceRepository approvedResourceRepository;

    /** The active approved resources, by id. */
    public Map<Long, AiApprovedResource> approved() {
        Map<Long, AiApprovedResource> approved = new LinkedHashMap<>();
        approvedResourceRepository.findByActiveTrueOrderByNameAsc().stream().limit(MAX_APPROVED)
                .forEach(resource -> approved.put(resource.getId(), resource));
        return approved;
    }

    /** The approved list as records for Claude, or nothing when there is none. */
    public static String describe(Map<Long, AiApprovedResource> approved) {
        if (approved.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("\nResources the program has approved:\n");
        approved.values().forEach(resource -> out.append("- [").append(resource.getId()).append("] ")
                .append(resource.getKind()).append(": ").append(clip(resource.getName()))
                .append(resource.getDescription() == null ? "" : " | " + clip(resource.getDescription())).append('\n'));
        return out.toString();
    }

    /** The instruction for the resource part of a request. */
    public static String instruction(int max) {
        return "Suggest up to " + max + " learning resources, tools, organisations, funding sources and kinds of people "
                + "that could help. Use any of the program's approved resources that fit first, with their id in approvedId; "
                + "for anything else put 0 in approvedId. Only name other things that are widely known and that you are "
                + "confident exist; otherwise describe the kind of resource. Never give web links, prices, phone numbers or "
                + "named private individuals. For people, describe the role.";
    }

    /** Claude's ideas, checked: approved ones with their own details, the rest without links and marked to check. */
    public static List<ResourceIdea> toIdeas(List<AiDrafts.ResourceDraft> drafts, Map<Long, AiApprovedResource> approved, int max) {
        List<ResourceIdea> ideas = new ArrayList<>();
        for (AiDrafts.ResourceDraft draft : Optional.ofNullable(drafts).orElse(List.of())) {
            String kind = draft.kind() == null ? "OTHER" : draft.kind().trim().toUpperCase();
            if (draft.name() == null || draft.name().isBlank() || ideas.size() >= max) {
                continue;
            }
            AiApprovedResource match = approved.get(draft.approvedId());
            if (match != null) {
                // Admin-checked (AI-FR-26): its own name, kind and link, not Claude's.
                if (ideas.stream().noneMatch(idea -> match.getName().equals(idea.name()))) {
                    ideas.add(new ResourceIdea(match.getKind(), match.getName(), limit(stripLinks(draft.why()), 300), false, match.getUrl()));
                }
                continue;
            }
            ideas.add(new ResourceIdea(RESOURCE_KINDS.contains(kind) ? kind : "OTHER", limit(stripLinks(draft.name()), 200),
                    limit(stripLinks(draft.why()), 300), true, null));
        }
        return ideas;
    }

    /** AI-FR-13: links are only passed on from the approved list. */
    static String stripLinks(String value) {
        return value == null ? null : value.replaceAll("(?i)\\b(https?://|www\\.)\\S+", "").trim();
    }

    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 3) + "...";
    }
}
