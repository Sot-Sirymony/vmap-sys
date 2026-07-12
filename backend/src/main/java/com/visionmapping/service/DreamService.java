package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.dto.request.DreamRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.DreamResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.service.support.ArchiveCascade;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PermanentDeleteCascade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meaningful future outcomes under a vision area. Archiving a dream marks it
 * ARCHIVED and cascades down to its goals, steps, and tasks.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DreamService {

    private final EntityLookup lookup;
    private final ArchiveCascade archiveCascade;
    private final PermanentDeleteCascade permanentDeleteCascade;
    private final VisionMappingMapper mapper;
    private final DreamRepository dreamRepository;

    @Transactional(readOnly = true)
    public List<DreamResponse> listDreams(boolean includeArchived) {
        List<Dream> entities = includeArchived
                ? dreamRepository.findByUser_Id(lookup.userId())
                : dreamRepository.findByUser_IdAndArchivedFalse(lookup.userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public DreamResponse createDream(DreamRequest request) {
        AppUser user = lookup.currentUser();
        VisionArea visionArea = lookup.visionArea(request.visionAreaId());
        Dream entity = Dream.builder()
                .code(nextCode("D", dreamRepository.findByUser_Id(user.getId()).size()))
                .user(user)
                .visionArea(visionArea)
                .title(request.title())
                .description(request.description())
                .whyImportant(request.whyImportant())
                .successDefinition(request.successDefinition())
                .dreamType(request.dreamType())
                .priority(request.priority())
                .targetDate(request.targetDate())
                .status(request.status())
                .build();
        return mapper.toResponse(dreamRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public DreamResponse getDream(Long id) {
        return mapper.toResponse(lookup.dream(id));
    }

    public DreamResponse updateDream(Long id, DreamRequest request) {
        Dream entity = lookup.dream(id);
        entity.setVisionArea(lookup.visionArea(request.visionAreaId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setWhyImportant(request.whyImportant());
        entity.setSuccessDefinition(request.successDefinition());
        entity.setDreamType(request.dreamType());
        entity.setPriority(request.priority());
        entity.setTargetDate(request.targetDate());
        entity.setStatus(request.status());
        return mapper.toResponse(entity);
    }

    public DreamResponse updateDreamStatus(Long id, String status) {
        Dream entity = lookup.dream(id);
        entity.setStatus(parseEnum(DreamStatus.class, status));
        return mapper.toResponse(entity);
    }

    public void archiveDream(Long id) {
        Dream entity = lookup.dream(id);
        entity.setStatus(DreamStatus.ARCHIVED);
        entity.setArchived(true);
        archiveCascade.archiveGoalsUnder(entity.getId());
    }

    @Transactional(readOnly = true)
    public ArchiveImpactResponse dreamArchiveImpact(Long id) {
        return archiveCascade.impactOfDream(lookup.dream(id));
    }

    public void restoreDream(Long id) {
        archiveCascade.unarchiveDreamChain(lookup.dream(id));
    }

    public void permanentlyDeleteDream(Long id) {
        Dream dream = lookup.dream(id);
        requireArchived(dream.isArchived(), "Dream");
        permanentDeleteCascade.deleteDream(dream);
    }
}
