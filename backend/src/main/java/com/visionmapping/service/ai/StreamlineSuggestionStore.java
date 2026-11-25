package com.visionmapping.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.response.StreamlineResponses;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.StreamlineSuggestionSet;
import com.visionmapping.repository.StreamlineReviewRepository;
import com.visionmapping.repository.StreamlineSuggestionSetRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V32 (D-32-1, D-32-2, SF-FR-01 to SF-FR-03): keeps the last checked AI
 * suggestions for each review and stage, so reopening a stage shows them at
 * once, with no new request.
 *
 * <p>A saved set belongs to the review's items: its fingerprint is the stage
 * and the ids and kinds of all the review's items, archived or not, so applying
 * a card (even a delete) never throws the set away. Each card is checked on its
 * own when it is read back instead, and a card whose item is gone, or whose
 * change is already made, is left out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamlineSuggestionStore {

    private static final TypeReference<List<StreamlineAiService.Suggestion>> CARDS = new TypeReference<>() {
    };

    private final StreamlineSuggestionSetRepository repository;
    private final StreamlineReviewRepository reviews;
    private final ObjectMapper objectMapper;

    /** What was saved, still valid for the review as it is now. */
    public record Saved(Long requestId, List<StreamlineAiService.Suggestion> cards, java.time.Instant savedAt, String source,
                        Integer draftedAtStage) {
        public Saved {
            cards = com.visionmapping.util.Copies.list(cards);
        }
    }

    /** Saves (or replaces) the set for this review and stage. */
    @Transactional
    public void save(AppUser user, StreamlineResponses.Review review, int stage, String source, String model, Long requestId,
                     List<StreamlineAiService.Suggestion> cards) {
        save(user, review, stage, source, model, requestId, cards, review.currentStage());
    }

    /** V32 phase 3: draftedAtStage is the review's current stage when the set was made. */
    @Transactional
    public void save(AppUser user, StreamlineResponses.Review review, int stage, String source, String model, Long requestId,
                     List<StreamlineAiService.Suggestion> cards, int draftedAtStage) {
        StreamlineSuggestionSet set = repository.findByReview_IdAndStage(review.id(), stage)
                .orElseGet(() -> StreamlineSuggestionSet.builder()
                        .user(user)
                        .review(reviews.getReferenceById(review.id()))
                        .stage(stage)
                        .build());
        set.setFingerprint(fingerprint(review, stage));
        set.setSource(source);
        set.setModel(model);
        set.setRequestId(requestId);
        set.setCards(write(cards));
        set.setDraftedAtStage(draftedAtStage);
        repository.save(set);
    }

    /**
     * BR-32-2: the saved cards that still fit the review, or empty. A set whose
     * fingerprint no longer matches is deleted here.
     */
    @Transactional
    public Optional<Saved> find(StreamlineResponses.Review review, int stage) {
        Optional<StreamlineSuggestionSet> found = repository.findByReview_IdAndStage(review.id(), stage);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        StreamlineSuggestionSet set = found.get();
        if (!set.getFingerprint().equals(fingerprint(review, stage))) {
            repository.delete(set);
            return Optional.empty();
        }
        List<StreamlineAiService.Suggestion> cards = read(set.getCards()).stream()
                .filter(card -> stillOpen(review, card))
                .toList();
        return Optional.of(new Saved(set.getRequestId(), cards, set.getUpdatedAt(), set.getSource(), set.getDraftedAtStage()));
    }

    /** A dismissed card ("Dismiss" or "Got it") is removed from the saved set. */
    @Transactional
    public void dismiss(Long reviewId, int stage, Long itemId, String action, Long targetItemId) {
        repository.findByReview_IdAndStage(reviewId, stage).ifPresent(set -> {
            List<StreamlineAiService.Suggestion> kept = new ArrayList<>(read(set.getCards()));
            boolean removed = kept.removeIf(card -> Objects.equals(card.itemId(), itemId) && card.action().equals(action)
                    && Objects.equals(card.targetItemId(), targetItemId));
            if (removed) {
                set.setCards(write(kept));
            }
        });
    }

    /** D-32-2: a review that is completed or abandoned keeps no saved suggestions. */
    @Transactional
    public void forget(Long reviewId) {
        repository.deleteByReviewId(reviewId);
    }

    /** SHA-256 of the stage and all the review's items (id and kind), in id order. */
    static String fingerprint(StreamlineResponses.Review review, int stage) {
        String text = "stage " + stage + ";" + review.items().stream()
                .sorted(Comparator.comparing(StreamlineResponses.Item::id))
                .map(item -> item.id() + ":" + item.kind())
                .collect(Collectors.joining(","));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * BR-32-3: a card is shown again only while its item (and a merge target)
     * is still active and the change it suggests has not been made already.
     */
    static boolean stillOpen(StreamlineResponses.Review review, StreamlineAiService.Suggestion card) {
        Map<Long, StreamlineResponses.Item> active = review.items().stream()
                .filter(item -> !item.archived())
                .collect(Collectors.toMap(StreamlineResponses.Item::id, Function.identity(), (first, second) -> first));
        StreamlineResponses.Item item = active.get(card.itemId());
        if (item == null) {
            return false;
        }
        return switch (card.action()) {
            case "VERDICT" -> item.verdict() == null;
            case "ANSWER" -> item.requirementSource() == null;
            case "DELETE", "KEEP" -> item.decision() == null;
            case "MERGE" -> item.decision() == null && active.containsKey(card.targetItemId());
            case "SIMPLIFY" -> !item.simplified();
            case "LEVER" -> card.lever() != null && (item.speedLevers() == null || !item.speedLevers().contains(card.lever()));
            case "REPEAT", "DELEGATE", "REMIND" -> item.automation() == null;
            default -> true;
        };
    }

    private String write(List<StreamlineAiService.Suggestion> cards) {
        try {
            return objectMapper.writeValueAsString(cards);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not save the suggestions", exception);
        }
    }

    private List<StreamlineAiService.Suggestion> read(String json) {
        try {
            return Optional.ofNullable(objectMapper.readValue(json, CARDS)).orElse(List.of());
        } catch (JsonProcessingException exception) {
            // A set saved by an older version that no longer reads is treated as no set.
            log.warn("Could not read saved Streamline suggestions: {}", exception.getOriginalMessage());
            return List.of();
        }
    }
}
