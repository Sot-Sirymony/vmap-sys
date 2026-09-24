package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.VisionAreaRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.VisionAreaResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.VisionAreaRepository;
import com.visionmapping.service.support.ArchiveCascade;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PermanentDeleteCascade;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The top of the hierarchy: a major life or work area. Archiving one cascades
 * ARCHIVED down through its dreams, goals, steps, and tasks.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class VisionAreaService {

    private final EntityLookup lookup;
    private final ArchiveCascade archiveCascade;
    private final PermanentDeleteCascade permanentDeleteCascade;
    private final VisionMappingMapper mapper;
    private final VisionAreaRepository visionAreaRepository;

    @Cacheable(CacheConfig.VISION_AREA_LIST_CACHE)
    @Transactional(readOnly = true)
    public List<VisionAreaResponse> listVisionAreas(boolean includeArchived) {
        return findAllForUser(visionAreaRepository, lookup.userId(), includeArchived).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public VisionAreaResponse createVisionArea(VisionAreaRequest request) {
        AppUser user = lookup.currentUser();
        VisionArea entity = VisionArea.builder()
                .code(nextCode("VA", visionAreaRepository.findCodesByUserId(user.getId())))
                .user(user)
                .name(request.name())
                .description(request.description())
                .visionStatement(request.visionStatement())
                .priority(request.priority())
                .letterRank(request.letterRank())
                .status(request.status())
                .sortOrder(Math.toIntExact(visionAreaRepository.countByUser_IdAndArchivedFalse(user.getId())))
                .build();
        return mapper.toResponse(visionAreaRepository.save(entity));
    }

    // Drag-and-drop reorder: orderedIds must be exactly this user's current
    // non-archived vision areas, just reshuffled — otherwise a stray or
    // missing id would silently orphan an area's position. No parent scope
    // here — a vision area is the top of the hierarchy.
    public void reorderVisionAreas(List<Long> orderedIds) {
        List<VisionArea> siblings = visionAreaRepository.findByUser_IdAndArchivedFalse(lookup.userId());
        Map<Long, VisionArea> byId = siblings.stream().collect(Collectors.toMap(VisionArea::getId, area -> area));
        if (orderedIds.size() != siblings.size() || !byId.keySet().containsAll(orderedIds)) {
            throw new BusinessRuleException("The given order must include exactly this user's current vision areas.");
        }
        for (int index = 0; index < orderedIds.size(); index++) {
            byId.get(orderedIds.get(index)).setSortOrder(index);
        }
    }

    @Cacheable(CacheConfig.VISION_AREA_CACHE)
    @Transactional(readOnly = true)
    public VisionAreaResponse getVisionArea(Long id) {
        return mapper.toResponse(lookup.visionArea(id));
    }

    public VisionAreaResponse updateVisionArea(Long id, VisionAreaRequest request) {
        VisionArea entity = lookup.visionArea(id);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setVisionStatement(request.visionStatement());
        entity.setPriority(request.priority());
        entity.setLetterRank(request.letterRank());
        entity.setStatus(request.status());
        return mapper.toResponse(entity);
    }

    public VisionAreaResponse updateVisionAreaStatus(Long id, String status) {
        VisionArea entity = lookup.visionArea(id);
        entity.setStatus(parseEnum(LifecycleStatus.class, status));
        return mapper.toResponse(entity);
    }

    public void archiveVisionArea(Long id) {
        VisionArea entity = lookup.visionArea(id);
        entity.setStatus(LifecycleStatus.ARCHIVED);
        entity.setArchived(true);
        archiveCascade.archiveDreamsUnder(entity.getId());
    }

    @Transactional(readOnly = true)
    public ArchiveImpactResponse visionAreaArchiveImpact(Long id) {
        return archiveCascade.impactOfVisionArea(lookup.visionArea(id));
    }

    public void restoreVisionArea(Long id) {
        archiveCascade.unarchiveVisionArea(lookup.visionArea(id));
    }

    public void permanentlyDeleteVisionArea(Long id) {
        VisionArea area = lookup.visionArea(id);
        requireArchived(area.isArchived(), "Vision area");
        permanentDeleteCascade.deleteVisionArea(area);
    }
}
