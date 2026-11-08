package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.dto.request.FearEntryRequest;
import com.visionmapping.dto.response.FearEntryResponse;
import com.visionmapping.entity.FearEntry;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.FearEntryRepository;
import com.visionmapping.service.support.ArchiveFilter;
import com.visionmapping.service.support.EntityLookup;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-65: the fear log. Private to its owner and never gates anything else. */
@Service
@Transactional
@RequiredArgsConstructor
public class FearEntryService {

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final FearEntryRepository fearEntryRepository;

    @Transactional(readOnly = true)
    public List<FearEntryResponse> listFearEntries(ArchiveFilter archiveFilter) {
        return findAllForUser(fearEntryRepository, lookup.userId(), archiveFilter).stream()
                .sorted(Comparator.comparing(FearEntry::getCreatedAt).reversed())
                .map(mapper::toResponse)
                .toList();
    }

    public FearEntryResponse createFearEntry(FearEntryRequest request) {
        FearEntry entity = FearEntry.builder()
                .user(lookup.currentUser())
                .fear(request.fear())
                .worstCase(request.worstCase())
                .likelihood(request.likelihood())
                .firstTest(request.firstTest())
                .relatedDream(lookup.optionalDream(request.relatedDreamId()))
                .build();
        return mapper.toResponse(fearEntryRepository.save(entity));
    }

    public FearEntryResponse updateFearEntry(Long id, FearEntryRequest request) {
        FearEntry entity = entry(id);
        entity.setFear(request.fear());
        entity.setWorstCase(request.worstCase());
        entity.setLikelihood(request.likelihood());
        entity.setFirstTest(request.firstTest());
        entity.setRelatedDream(lookup.optionalDream(request.relatedDreamId()));
        return mapper.toResponse(entity);
    }

    public void archiveFearEntry(Long id) {
        entry(id).setArchived(true);
    }

    public void restoreFearEntry(Long id) {
        entry(id).setArchived(false);
    }

    public void permanentlyDeleteFearEntry(Long id) {
        FearEntry entity = entry(id);
        requireArchived(entity.isArchived(), "Fear entry");
        fearEntryRepository.delete(entity);
    }

    private FearEntry entry(Long id) {
        return fearEntryRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Fear entry not found: " + id));
    }
}
