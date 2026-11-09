package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.isBlank;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.request.GapCoverRequest;
import com.visionmapping.dto.request.PartnerRequest;
import com.visionmapping.dto.request.SelfAssessmentItemRequest;
import com.visionmapping.dto.request.VisionStepRequest;
import com.visionmapping.dto.response.GapCoverRemovalResponse;
import com.visionmapping.dto.response.SelfAssessmentItemResponse;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.SelfAssessmentItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.GapCoverMethod;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Relevance;
import com.visionmapping.entity.enums.SelfAssessmentSource;
import com.visionmapping.entity.enums.StrengthKind;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.SelfAssessmentItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.ArchiveFilter;
import com.visionmapping.service.support.EntityLookup;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V8.1 FR-79 to FR-83: the items of the Strengths and Gaps profile. Each needs
 * evidence (BR-61). Only a high-relevance weakness is given a way to cover it,
 * and exactly one (FR-82); "Learn" is limited to {@value #LEARN_LIMIT} at a
 * time (BR-62), which is the rule that stops the user trying to fix every gap.
 * Private to the account like the journals (BR-64): no coach endpoint reads it.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SelfAssessmentService {

    /** BR-62. The BRD leaves the number open; 2 is its proposed default. */
    public static final int LEARN_LIMIT = 2;

    static final int RESOURCE_MODULE = 6;
    static final int LEVERAGE_MODULE = 7;
    private static final int MAX_STEP_TITLE = 220;
    private static final TypeReference<Map<String, String>> ROW = new TypeReference<>() { };

    /** Strengths before weaknesses (NFR-11), then high relevance first, then oldest first. */
    static final Comparator<SelfAssessmentItem> DISPLAY_ORDER = Comparator
            .comparing(SelfAssessmentItem::getKind)
            .thenComparing(SelfAssessmentItem::getRelevance)
            .thenComparing(SelfAssessmentItem::getId);

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final ObjectMapper objectMapper;
    private final SelfAssessmentItemRepository itemRepository;
    private final VisionStepRepository visionStepRepository;
    private final PartnerService partnerService;
    private final VisionStepService visionStepService;
    private final LearningModuleService learningModuleService;

    @Transactional(readOnly = true)
    public List<SelfAssessmentItemResponse> listItems(@Nullable Long dreamId, ArchiveFilter archiveFilter) {
        Long userId = lookup.userId();
        List<SelfAssessmentItem> items = dreamId == null
                ? itemRepository.findByUser_Id(userId)
                : itemRepository.findByUser_IdAndDream_Id(userId, lookup.dream(dreamId).getId());
        return items.stream()
                .filter(item -> archiveFilter.includesArchived() || !item.isArchived())
                .sorted(DISPLAY_ORDER)
                .map(mapper::toResponse)
                .toList();
    }

    public SelfAssessmentItemResponse createItem(SelfAssessmentItemRequest request) {
        SelfAssessmentItem item = SelfAssessmentItem.builder()
                .user(lookup.currentUser())
                .dream(lookup.dream(request.dreamId()))
                .source(request.source() == null ? SelfAssessmentSource.MANUAL : request.source())
                .build();
        apply(item, request);
        return mapper.toResponse(itemRepository.save(item));
    }

    public SelfAssessmentItemResponse updateItem(Long id, SelfAssessmentItemRequest request) {
        SelfAssessmentItem item = item(id);
        boolean wasHighWeakness = item.isHighWeakness();
        Dream dream = lookup.dream(request.dreamId());
        if (!dream.getId().equals(item.getDream().getId()) && item.getCoverMethod() != null) {
            throw new BusinessRuleException("Remove the way this gap is covered before moving it to another dream.");
        }
        item.setDream(dream);
        apply(item, request);
        if (item.getKind() == StrengthKind.STRENGTH) {
            // A strength is not covered; the record a cover created stays where it is.
            item.clearCover();
        } else if (!wasHighWeakness && item.isHighWeakness() && item.getCoverMethod() == GapCoverMethod.LEARN) {
            requireLearnRoom(item.getId());
        }
        return mapper.toResponse(item);
    }

    /** FR-82: sets the one way this gap is covered and creates the record that way needs. */
    public SelfAssessmentItemResponse setCover(Long id, GapCoverRequest request) {
        SelfAssessmentItem item = item(id);
        if (item.isArchived()) {
            throw new BusinessRuleException("Restore this item before choosing how to cover it.");
        }
        if (!item.isHighWeakness()) {
            throw new BusinessRuleException("Only a weakness that matters a lot to this dream needs a way to cover it.");
        }
        if (item.getCoverMethod() != null) {
            throw new BusinessRuleException("This gap already has a way to cover it. Remove that first to choose another.");
        }
        switch (request.method()) {
            case PARTNER -> coverWithPartner(item, request);
            case DELEGATE -> coverWithRow(item, LEVERAGE_MODULE, requireNote(request, "Say who or what will carry it."),
                    leverageRow(item.getDescription(), request.note().strip()));
            case RESOURCE -> coverWithRow(item, RESOURCE_MODULE, requireNote(request, "Say who or what could supply it."),
                    resourceRow(item.getDescription(), request.note().strip()));
            case LEARN -> coverByLearning(item, request);
            case ACCEPT -> item.setCoverNote(requireNote(request, "Write one line on why this gap does not matter enough."));
        }
        item.setCoverMethod(request.method());
        return mapper.toResponse(item);
    }

    /** Clears this gap's cover. With removeLinked, the record it created is archived or removed too. */
    public GapCoverRemovalResponse removeCover(Long id, boolean removeLinked) {
        SelfAssessmentItem item = item(id);
        GapCoverRemovalResponse result = removeLinkedRecord(item, removeLinked);
        item.clearCover();
        return result;
    }

    /**
     * Archives the item (AC-10). With removeLinked, the record its cover created
     * is archived or removed too, and the cover is cleared so a restored item
     * does not point at it.
     */
    public GapCoverRemovalResponse archiveItem(Long id, boolean removeLinked) {
        SelfAssessmentItem item = item(id);
        GapCoverRemovalResponse result = removeLinkedRecord(item, removeLinked);
        if (removeLinked) {
            item.clearCover();
        }
        item.setArchived(true);
        return result;
    }

    public void restoreItem(Long id) {
        SelfAssessmentItem item = item(id);
        if (item.isHighWeakness() && item.getCoverMethod() == GapCoverMethod.LEARN) {
            requireLearnRoom(item.getId());
        }
        item.setArchived(false);
    }

    public void permanentlyDeleteItem(Long id) {
        SelfAssessmentItem item = item(id);
        requireArchived(item.isArchived(), "Strength or gap");
        itemRepository.delete(item);
    }

    private void apply(SelfAssessmentItem item, SelfAssessmentItemRequest request) {
        item.setKind(request.kind());
        item.setDescription(request.description().strip());
        item.setEvidence(request.evidence().strip());
        item.setRelevance(request.relevance());
        boolean hasOutsideView = !isBlank(request.outsideView());
        item.setOutsideView(hasOutsideView ? request.outsideView().strip() : null);
        item.setOutsideBy(hasOutsideView && !isBlank(request.outsideBy()) ? request.outsideBy().strip() : null);
        item.setOutsideAgrees(hasOutsideView ? request.outsideAgrees() : null);
    }

    private void coverWithPartner(SelfAssessmentItem item, GapCoverRequest request) {
        if (isBlank(request.partnerName())) {
            throw new BusinessRuleException("Give the partner's name.");
        }
        PartnerSupportType supportType = request.partnerSupportType() == null ? PartnerSupportType.OTHER : request.partnerSupportType();
        String notes = "Covers a gap from my Strengths and Gaps profile: " + item.getDescription();
        Long partnerId = partnerService.createPartner(new PartnerRequest(request.partnerName().strip(), null, null, null, null,
                null, supportType, null, null, item.getDream().getId(), null, null, null, PartnerStatus.TO_CONTACT, notes,
                null, null, null, null, null, null, null, null, null, null)).id();
        item.setLinkedPartner(lookup.partner(partnerId));
        item.setCoverNote(isBlank(request.note()) ? null : request.note().strip());
    }

    private void coverWithRow(SelfAssessmentItem item, int moduleNumber, String note, Map<String, String> row) {
        learningModuleService.appendExerciseRow(moduleNumber, row);
        item.setCoverNote(note);
        item.setLinkedModuleNumber(moduleNumber);
        item.setLinkedModuleRow(write(row));
    }

    /** BR-62 and FR-82: a dated step on one of this dream's goals, owned by the account holder. */
    private void coverByLearning(SelfAssessmentItem item, GapCoverRequest request) {
        if (request.goalId() == null || request.targetDate() == null) {
            throw new BusinessRuleException("Choose a goal and a date for the learning step.");
        }
        requireLearnRoom(item.getId());
        Goal goal = lookup.goal(request.goalId());
        if (!goal.getDream().getId().equals(item.getDream().getId())) {
            throw new BusinessRuleException("Choose one of this dream's goals.");
        }
        int nextSequence = visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), lookup.userId()).stream()
                .map(VisionStep::getSequenceNumber)
                .max(Integer::compare)
                .orElse(0) + 1;
        String title = truncate("Learn: " + item.getDescription(), MAX_STEP_TITLE);
        String description = isBlank(request.note()) ? null : request.note().strip();
        Long stepId = visionStepService.createStep(new VisionStepRequest(goal.getId(), title, description,
                lookup.currentUser().getFullName(), nextSequence, false, Priority.MEDIUM, request.targetDate(),
                WorkStatus.NOT_STARTED, null, null, null)).id();
        item.setLinkedStep(lookup.step(stepId));
        item.setCoverNote(description);
    }

    private void requireLearnRoom(Long itemId) {
        long inUse = itemRepository.findByUser_IdAndArchivedFalse(lookup.userId()).stream()
                .filter(other -> !other.getId().equals(itemId))
                .filter(other -> other.isHighWeakness() && other.getCoverMethod() == GapCoverMethod.LEARN)
                .count();
        if (inUse >= LEARN_LIMIT) {
            throw new BusinessRuleException(("You are already learning %d gaps. Cover this one another way: "
                    + "a partner, delegating it, a resource, or accepting it.").formatted(LEARN_LIMIT));
        }
    }

    private GapCoverRemovalResponse removeLinkedRecord(SelfAssessmentItem item, boolean removeLinked) {
        GapCoverMethod method = item.getCoverMethod();
        if (!removeLinked || method == null || method == GapCoverMethod.ACCEPT) {
            return new GapCoverRemovalResponse(false, null);
        }
        return switch (method) {
            case PARTNER -> {
                if (item.getLinkedPartner() == null) {
                    yield new GapCoverRemovalResponse(false, "The partner had already been deleted.");
                }
                partnerService.archivePartner(item.getLinkedPartner().getId());
                yield new GapCoverRemovalResponse(true, null);
            }
            case LEARN -> {
                if (item.getLinkedStep() == null) {
                    yield new GapCoverRemovalResponse(false, "The learning step had already been deleted.");
                }
                visionStepService.archiveStep(item.getLinkedStep().getId());
                yield new GapCoverRemovalResponse(true, null);
            }
            default -> {
                String why = item.getLinkedModuleRow() == null
                        ? "There was no exercise row to remove."
                        : learningModuleService.removeExerciseRow(item.getLinkedModuleNumber(), read(item.getLinkedModuleRow()));
                yield new GapCoverRemovalResponse(why == null, why);
            }
        };
    }

    private static String requireNote(GapCoverRequest request, String message) {
        if (isBlank(request.note())) {
            throw new BusinessRuleException(message);
        }
        return request.note().strip();
    }

    /** Module 6's fields: what is missing, and who or what could supply it. */
    static Map<String, String> resourceRow(String gap, String source) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("gap", gap);
        row.put("source", source);
        return row;
    }

    /** Module 7's fields: the task, how it is multiplied, and who or what carries it. */
    static Map<String, String> leverageRow(String task, String carrier) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("task", task);
        row.put("approach", "Delegate or automate");
        row.put("carrier", carrier);
        return row;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1).strip() + "…";
    }

    private String write(Map<String, String> row) {
        try {
            return objectMapper.writeValueAsString(row);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the exercise row", exception);
        }
    }

    private Map<String, String> read(String json) {
        try {
            return objectMapper.readValue(json, ROW);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read the stored exercise row", exception);
        }
    }

    private SelfAssessmentItem item(Long id) {
        return itemRepository.findById(id)
                .filter(item -> item.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Strength or gap not found: " + id));
    }

    /** Used by the profile so the order is the same everywhere. */
    static List<SelfAssessmentItem> sorted(List<SelfAssessmentItem> items) {
        return items.stream().sorted(DISPLAY_ORDER).toList();
    }
}
