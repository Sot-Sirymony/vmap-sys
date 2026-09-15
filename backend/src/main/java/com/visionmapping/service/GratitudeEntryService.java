package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.GratitudeEntryRequest;
import com.visionmapping.dto.response.GratitudeEntryResponse;
import com.visionmapping.entity.GratitudeEntry;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.GratitudeEntryRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-59.1: a lightweight, optional log of what's already going well — the
 * counterweight to a system that otherwise only tracks what's unfinished,
 * blocked, or overdue. Every field is diagnostic; nothing here gates
 * anything else (BR-48).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class GratitudeEntryService {

    private static final int RECENT_WINDOW_DAYS = 7;
    private static final int RECENT_ENTRY_LIMIT = 3;

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final GratitudeEntryRepository gratitudeEntryRepository;
    private final Clock clock;

    @Cacheable(CacheConfig.GRATITUDE_ENTRY_LIST_CACHE)
    @Transactional(readOnly = true)
    public List<GratitudeEntryResponse> listGratitudeEntries(boolean includeArchived) {
        return findAllForUser(gratitudeEntryRepository, lookup.userId(), includeArchived).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public GratitudeEntryResponse createGratitudeEntry(GratitudeEntryRequest request) {
        GratitudeEntry entity = GratitudeEntry.builder()
                .user(lookup.currentUser())
                .category(request.category())
                .description(request.description())
                .relatedDream(lookup.optionalDream(request.relatedDreamId()))
                .relatedGoal(lookup.optionalGoal(request.relatedGoalId()))
                .build();
        return mapper.toResponse(gratitudeEntryRepository.save(entity));
    }

    @Cacheable(CacheConfig.GRATITUDE_ENTRY_CACHE)
    @Transactional(readOnly = true)
    public GratitudeEntryResponse getGratitudeEntry(Long id) {
        return mapper.toResponse(entry(id));
    }

    public GratitudeEntryResponse updateGratitudeEntry(Long id, GratitudeEntryRequest request) {
        GratitudeEntry entity = entry(id);
        entity.setCategory(request.category());
        entity.setDescription(request.description());
        entity.setRelatedDream(lookup.optionalDream(request.relatedDreamId()));
        entity.setRelatedGoal(lookup.optionalGoal(request.relatedGoalId()));
        return mapper.toResponse(entity);
    }

    public void archiveGratitudeEntry(Long id) {
        entry(id).setArchived(true);
    }

    public void restoreGratitudeEntry(Long id) {
        entry(id).setArchived(false);
    }

    public void permanentlyDeleteGratitudeEntry(Long id) {
        GratitudeEntry entity = entry(id);
        requireArchived(entity.isArchived(), "Gratitude entry");
        gratitudeEntryRepository.delete(entity);
    }

    /**
     * FR-59.2: this week's count and the two or three most recent entries,
     * for the dashboard card. "This week" is a rolling 7 days, not a
     * calendar-week boundary — simplest to reason about and to test.
     */
    @Transactional(readOnly = true)
    public GratitudeSummary summary() {
        Instant since = Instant.now(clock).minus(RECENT_WINDOW_DAYS, ChronoUnit.DAYS);
        List<GratitudeEntry> recentWindow =
                gratitudeEntryRepository.findByUser_IdAndArchivedFalseAndCreatedAtAfterOrderByCreatedAtDesc(lookup.userId(), since);
        List<GratitudeEntryResponse> recent =
                gratitudeEntryRepository.findByUser_IdAndArchivedFalseOrderByCreatedAtDesc(lookup.userId()).stream()
                        .limit(RECENT_ENTRY_LIMIT)
                        .map(mapper::toResponse)
                        .toList();
        return new GratitudeSummary(recentWindow.size(), recent);
    }

    public record GratitudeSummary(long countThisWeek, List<GratitudeEntryResponse> recent) {
    }

    private GratitudeEntry entry(Long id) {
        return gratitudeEntryRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Gratitude entry not found: " + id));
    }
}
