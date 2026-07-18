package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.dto.request.VisionAreaRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.VisionAreaResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.VisionAreaRepository;
import com.visionmapping.service.support.ArchiveCascade;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PermanentDeleteCascade;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

    @Transactional(readOnly = true)
    public List<VisionAreaResponse> listVisionAreas(boolean includeArchived) {
        return findAllForUser(visionAreaRepository, lookup.userId(), includeArchived).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public VisionAreaResponse createVisionArea(VisionAreaRequest request) {
        AppUser user = lookup.currentUser();
        VisionArea entity = VisionArea.builder()
                .code(nextCode("VA", visionAreaRepository.findByUser_Id(user.getId()), VisionArea::getCode))
                .user(user)
                .name(request.name())
                .description(request.description())
                .priority(request.priority())
                .status(request.status())
                .build();
        return mapper.toResponse(visionAreaRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public VisionAreaResponse getVisionArea(Long id) {
        return mapper.toResponse(lookup.visionArea(id));
    }

    public VisionAreaResponse updateVisionArea(Long id, VisionAreaRequest request) {
        VisionArea entity = lookup.visionArea(id);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setPriority(request.priority());
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
