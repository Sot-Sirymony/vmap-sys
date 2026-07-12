package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.dto.request.PartnerRequest;
import com.visionmapping.dto.response.PartnerResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.CommunicationMessage;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.service.support.EntityLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * People and resources that can help, linked to any level of the hierarchy.
 * Permanently deleting a partner unlinks it from obstacles and messages that
 * point at it (those records survive) before removing it.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PartnerService {

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final PartnerRepository partnerRepository;
    private final ObstacleRepository obstacleRepository;
    private final CommunicationMessageRepository communicationMessageRepository;

    @Transactional(readOnly = true)
    public Page<PartnerResponse> listPartners(Pageable pageable, boolean includeArchived) {
        Page<Partner> entities = includeArchived
                ? partnerRepository.findByUser_Id(lookup.userId(), pageable)
                : partnerRepository.findByUser_IdAndArchivedFalse(lookup.userId(), pageable);
        return entities.map(mapper::toResponse);
    }

    public PartnerResponse createPartner(PartnerRequest request) {
        AppUser user = lookup.currentUser();
        Partner entity = Partner.builder()
                .code(nextCode("P", partnerRepository.findByUser_Id(user.getId()).size()))
                .user(user)
                .name(request.name())
                .role(request.role())
                .organization(request.organization())
                .email(request.email())
                .phone(request.phone())
                .strength(request.strength())
                .supportType(request.supportType())
                .relatedVisionArea(lookup.optionalVisionArea(request.relatedVisionAreaId()))
                .relatedDream(lookup.optionalDream(request.relatedDreamId()))
                .relatedGoal(lookup.optionalGoal(request.relatedGoalId()))
                .relatedStep(lookup.optionalStep(request.relatedStepId()))
                .relatedTask(lookup.optionalTask(request.relatedTaskId()))
                .status(request.status())
                .notes(request.notes())
                .build();
        return mapper.toResponse(partnerRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public PartnerResponse getPartner(Long id) {
        return mapper.toResponse(lookup.partner(id));
    }

    public PartnerResponse updatePartner(Long id, PartnerRequest request) {
        Partner entity = lookup.partner(id);
        entity.setName(request.name());
        entity.setRole(request.role());
        entity.setOrganization(request.organization());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setStrength(request.strength());
        entity.setSupportType(request.supportType());
        entity.setRelatedVisionArea(lookup.optionalVisionArea(request.relatedVisionAreaId()));
        entity.setRelatedDream(lookup.optionalDream(request.relatedDreamId()));
        entity.setRelatedGoal(lookup.optionalGoal(request.relatedGoalId()));
        entity.setRelatedStep(lookup.optionalStep(request.relatedStepId()));
        entity.setRelatedTask(lookup.optionalTask(request.relatedTaskId()));
        entity.setStatus(request.status());
        entity.setNotes(request.notes());
        return mapper.toResponse(entity);
    }

    public PartnerResponse updatePartnerStatus(Long id, String status) {
        Partner entity = lookup.partner(id);
        entity.setStatus(parseEnum(PartnerStatus.class, status));
        return mapper.toResponse(entity);
    }

    public void archivePartner(Long id) {
        lookup.partner(id).setArchived(true);
    }

    public void restorePartner(Long id) {
        lookup.partner(id).setArchived(false);
    }

    public void permanentlyDeletePartner(Long id) {
        Partner partner = lookup.partner(id);
        requireArchived(partner.isArchived(), "Partner");
        Long partnerId = partner.getId();
        for (Obstacle obstacle : obstacleRepository.findByUser_Id(lookup.userId())) {
            if (obstacle.getRequiredPartner() != null && obstacle.getRequiredPartner().getId().equals(partnerId)) {
                obstacle.setRequiredPartner(null);
            }
        }
        for (CommunicationMessage message : communicationMessageRepository.findByUser_Id(lookup.userId())) {
            if (message.getPartner() != null && message.getPartner().getId().equals(partnerId)) {
                message.setPartner(null);
            }
        }
        partnerRepository.delete(partner);
    }
}
