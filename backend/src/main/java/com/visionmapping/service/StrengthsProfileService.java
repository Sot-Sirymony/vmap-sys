package com.visionmapping.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.response.StrengthsProfileResponse;
import com.visionmapping.dto.response.StrengthsProfileResponse.Change;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.SelfAssessmentItem;
import com.visionmapping.entity.SelfAssessmentSnapshot;
import com.visionmapping.entity.enums.GapCoverMethod;
import com.visionmapping.entity.enums.Relevance;
import com.visionmapping.entity.enums.StrengthKind;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.SelfAssessmentItemRepository;
import com.visionmapping.repository.SelfAssessmentSnapshotRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V8.1 FR-84: the one-page Strengths and Gaps profile for one dream, and the
 * saved copies a retake compares against. It reads the items but never changes
 * them, and it never holds back plan readiness (BR-63).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StrengthsProfileService {

    /** Same interval as the six-chain retake (ChainAssessmentService), so both are offered together. */
    static final int RETAKE_AFTER_DAYS = 84;
    private static final int MAX_SNAPSHOT_LENGTH = 20000;
    private static final TypeReference<List<SavedItem>> SAVED_ITEMS = new TypeReference<>() { };

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SelfAssessmentItemRepository itemRepository;
    private final SelfAssessmentSnapshotRepository snapshotRepository;

    public StrengthsProfileResponse profile(Long dreamId) {
        Dream dream = lookup.dream(dreamId);
        Long userId = lookup.userId();
        List<SelfAssessmentItem> items = SelfAssessmentService.sorted(
                itemRepository.findByUser_IdAndDream_IdAndArchivedFalse(userId, dream.getId()));
        LocalDate today = LocalDate.now(clock);
        List<SelfAssessmentSnapshot> saves = snapshotRepository.findByUser_IdAndDream_IdOrderByTakenOnDesc(userId, dream.getId());
        Optional<SelfAssessmentSnapshot> latest = saves.stream().findFirst();
        Optional<SelfAssessmentSnapshot> earlier = saves.stream()
                .filter(snapshot -> snapshot.getTakenOn().isBefore(today))
                .findFirst();
        long learnInUse = itemRepository.countByUser_IdAndArchivedFalseAndKindAndRelevanceAndCoverMethod(
                userId, StrengthKind.WEAKNESS, Relevance.HIGH, GapCoverMethod.LEARN);
        return new StrengthsProfileResponse(
                dream.getId(),
                dream.getTitle(),
                items.stream().filter(item -> item.getKind() == StrengthKind.STRENGTH && item.getRelevance() == Relevance.HIGH)
                        .map(mapper::toResponse).toList(),
                items.stream().filter(SelfAssessmentItem::isHighWeakness).map(mapper::toResponse).toList(),
                items.stream().filter(item -> item.getRelevance() != Relevance.HIGH).map(mapper::toResponse).toList(),
                isComplete(items),
                (int) uncoveredGaps(items),
                learnInUse,
                SelfAssessmentService.LEARN_LIMIT,
                latest.map(SelfAssessmentSnapshot::getTakenOn).orElse(null),
                earlier.map(SelfAssessmentSnapshot::getTakenOn).orElse(null),
                latest.map(snapshot -> !snapshot.getTakenOn().plusDays(RETAKE_AFTER_DAYS).isAfter(today)).orElse(false),
                earlier.map(snapshot -> changes(read(snapshot.getItemsJson()), items)).orElse(List.of()));
    }

    /** Saves the profile as it stands today. A second save on the same day replaces that day's. */
    @Transactional
    public StrengthsProfileResponse saveSnapshot(Long dreamId) {
        Dream dream = lookup.dream(dreamId);
        Long userId = lookup.userId();
        List<SelfAssessmentItem> items = SelfAssessmentService.sorted(
                itemRepository.findByUser_IdAndDream_IdAndArchivedFalse(userId, dream.getId()));
        if (items.isEmpty()) {
            throw new BusinessRuleException("Add at least one strength or gap before saving the profile.");
        }
        LocalDate today = LocalDate.now(clock);
        SelfAssessmentSnapshot snapshot = snapshotRepository.findByUser_IdAndDream_IdAndTakenOn(userId, dream.getId(), today)
                .orElseGet(() -> SelfAssessmentSnapshot.builder().user(lookup.currentUser()).dream(dream).takenOn(today).build());
        snapshot.setItemsJson(write(items.stream().map(SavedItem::of).toList()));
        snapshotRepository.save(snapshot);
        return profile(dreamId);
    }

    /** BR-63, for the Plan page checklist: the same rule the profile shows. */
    public ProfileState stateFor(Long userId, Long dreamId) {
        List<SelfAssessmentItem> items = itemRepository.findByUser_IdAndDream_IdAndArchivedFalse(userId, dreamId);
        return new ProfileState(!items.isEmpty(), isComplete(items), uncoveredGaps(items));
    }

    /** What the Plan page and PDF need to know. started means at least one item exists. */
    public record ProfileState(boolean started, boolean complete, long uncoveredGaps) {
    }

    /** BR-63: at least one strength and one weakness, and every high-relevance weakness covered. */
    static boolean isComplete(List<SelfAssessmentItem> items) {
        boolean anyStrength = items.stream().anyMatch(item -> item.getKind() == StrengthKind.STRENGTH);
        boolean anyWeakness = items.stream().anyMatch(item -> item.getKind() == StrengthKind.WEAKNESS);
        return anyStrength && anyWeakness && uncoveredGaps(items) == 0;
    }

    private static long uncoveredGaps(List<SelfAssessmentItem> items) {
        return items.stream().filter(item -> item.isHighWeakness() && item.getCoverMethod() == null).count();
    }

    /** Items are matched by kind and description, ignoring case and outer spaces. */
    static List<Change> changes(List<SavedItem> before, List<SelfAssessmentItem> now) {
        Map<String, SavedItem> earlier = new LinkedHashMap<>();
        before.forEach(item -> earlier.putIfAbsent(key(item.kind(), item.description()), item));
        List<Change> changes = new ArrayList<>();
        for (SelfAssessmentItem item : now) {
            SavedItem old = earlier.remove(key(item.getKind(), item.getDescription()));
            if (old == null) {
                changes.add(new Change("ADDED", item.getKind(), item.getDescription(), null, item.getRelevance(), null, item.getCoverMethod()));
            } else if (old.relevance() != item.getRelevance() || !Objects.equals(old.coverMethod(), item.getCoverMethod())) {
                changes.add(new Change("CHANGED", item.getKind(), item.getDescription(), old.relevance(), item.getRelevance(),
                        old.coverMethod(), item.getCoverMethod()));
            }
        }
        earlier.values().forEach(old -> changes.add(new Change("REMOVED", old.kind(), old.description(), old.relevance(), null,
                old.coverMethod(), null)));
        return changes;
    }

    private static String key(StrengthKind kind, String description) {
        return kind + "|" + description.strip().toLowerCase(Locale.ROOT);
    }

    private String write(List<SavedItem> items) {
        try {
            String json = objectMapper.writeValueAsString(items);
            if (json.length() > MAX_SNAPSHOT_LENGTH) {
                throw new BusinessRuleException("This profile is too long to save. Remove a few items that are recorded only.");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the profile", exception);
        }
    }

    private List<SavedItem> read(String json) {
        try {
            return objectMapper.readValue(json, SAVED_ITEMS);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read a saved profile", exception);
        }
    }

    /** One item as it was saved. Only what a retake compares is kept. */
    record SavedItem(StrengthKind kind, String description, Relevance relevance, @Nullable GapCoverMethod coverMethod) {

        static SavedItem of(SelfAssessmentItem item) {
            return new SavedItem(item.getKind(), item.getDescription(), item.getRelevance(), item.getCoverMethod());
        }
    }
}
