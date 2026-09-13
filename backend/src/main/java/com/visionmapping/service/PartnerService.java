package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.isBlank;
import static com.visionmapping.service.support.ServiceSupport.likeTerm;
import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.PartnerRequest;
import com.visionmapping.dto.response.PartnerResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<PartnerResponse> listPartners(Pageable pageable, boolean includeArchived, String search) {
        return listPartners(pageable, includeArchived, search, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<PartnerResponse> listPartners(
            Pageable pageable,
            boolean includeArchived,
            String search,
            PartnerSupportType supportType,
            PartnerStatus status,
            Long dreamId) {
        return partnerRepository
                .findFiltered(lookup.userId(), includeArchived, supportType, status, dreamId, likeTerm(search), pageable)
                .map(mapper::toResponse);
    }

    public PartnerResponse createPartner(PartnerRequest request) {
        AppUser user = lookup.currentUser();
        Partner entity = Partner.builder()
                .code(nextCode("P", partnerRepository.findByUser_Id(user.getId()), Partner::getCode))
                .user(user)
                .name(request.name())
                .role(request.role())
                .organization(request.organization())
                .email(request.email())
                .phone(request.phone())
                .strength(request.strength())
                .supportType(request.supportType())
                .offerType(request.offerType())
                .relatedVisionArea(lookup.optionalVisionArea(request.relatedVisionAreaId()))
                .relatedDream(lookup.optionalDream(request.relatedDreamId()))
                .relatedGoal(lookup.optionalGoal(request.relatedGoalId()))
                .relatedStep(lookup.optionalStep(request.relatedStepId()))
                .relatedTask(lookup.optionalTask(request.relatedTaskId()))
                .status(request.status())
                .notes(request.notes())
                .flagDishonesty(request.flagDishonesty())
                .flagAnger(request.flagAnger())
                .flagPoorJudgment(request.flagPoorJudgment())
                .flagOutsizedReward(request.flagOutsizedReward())
                .flagFlatteryPressure(request.flagFlatteryPressure())
                .flagGossip(request.flagGossip())
                .flagDisregardBoundaries(request.flagDisregardBoundaries())
                .riskOverrideNote(request.riskOverrideNote())
                .primaryMotivator(request.primaryMotivator())
                .workStyleType(request.workStyleType())
                .build();
        prepareForActive(entity);
        return mapper.toResponse(partnerRepository.save(entity));
    }

    @Cacheable(CacheConfig.PARTNER_CACHE)
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
        entity.setOfferType(request.offerType());
        entity.setRelatedVisionArea(lookup.optionalVisionArea(request.relatedVisionAreaId()));
        entity.setRelatedDream(lookup.optionalDream(request.relatedDreamId()));
        entity.setRelatedGoal(lookup.optionalGoal(request.relatedGoalId()));
        entity.setRelatedStep(lookup.optionalStep(request.relatedStepId()));
        entity.setRelatedTask(lookup.optionalTask(request.relatedTaskId()));
        entity.setStatus(request.status());
        entity.setNotes(request.notes());
        entity.setFlagDishonesty(request.flagDishonesty());
        entity.setFlagAnger(request.flagAnger());
        entity.setFlagPoorJudgment(request.flagPoorJudgment());
        entity.setFlagOutsizedReward(request.flagOutsizedReward());
        entity.setFlagFlatteryPressure(request.flagFlatteryPressure());
        entity.setFlagGossip(request.flagGossip());
        entity.setFlagDisregardBoundaries(request.flagDisregardBoundaries());
        entity.setRiskOverrideNote(request.riskOverrideNote());
        entity.setPrimaryMotivator(request.primaryMotivator());
        entity.setWorkStyleType(request.workStyleType());
        prepareForActive(entity);
        return mapper.toResponse(entity);
    }

    public PartnerResponse updatePartnerStatus(Long id, String status) {
        Partner entity = lookup.partner(id);
        entity.setStatus(parseEnum(PartnerStatus.class, status));
        prepareForActive(entity);
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
        unlinkPartnerReferences(partner.getId());
        partnerRepository.delete(partner);
    }

    /**
     * BR-39 / FR-50.2-3: the first time a FINANCIAL or TECHNICAL partner
     * moves to ACTIVE, any flagged integrity concern requires a non-blank
     * riskOverrideNote before the transition is allowed. Once cleared,
     * {@code vettedAt} is stamped and the gate never re-fires for this
     * partner — matching {@code ObstacleService.prepareObstacle}'s pattern
     * of checking the entity's already-set fields on every path that can
     * change status, including the quick-status PATCH.
     */
    private void prepareForActive(Partner entity) {
        boolean needsVetting = entity.getStatus() == PartnerStatus.ACTIVE
                && (entity.getSupportType() == PartnerSupportType.FINANCIAL
                        || entity.getSupportType() == PartnerSupportType.TECHNICAL)
                && entity.getVettedAt() == null;
        if (!needsVetting) {
            return;
        }
        if (hasIntegrityFlag(entity) && isBlank(entity.getRiskOverrideNote())) {
            throw new BusinessRuleException(
                    "This partner has one or more integrity concerns flagged. Record why you are proceeding anyway before moving them to Active.");
        }
        entity.setVettedAt(Instant.now(clock));
    }

    private boolean hasIntegrityFlag(Partner entity) {
        return Boolean.TRUE.equals(entity.getFlagDishonesty())
                || Boolean.TRUE.equals(entity.getFlagAnger())
                || Boolean.TRUE.equals(entity.getFlagPoorJudgment())
                || Boolean.TRUE.equals(entity.getFlagOutsizedReward())
                || Boolean.TRUE.equals(entity.getFlagFlatteryPressure())
                || Boolean.TRUE.equals(entity.getFlagGossip())
                || Boolean.TRUE.equals(entity.getFlagDisregardBoundaries());
    }

    /** Obstacles and messages that point at the partner survive the delete; only their link is cleared. */
    private void unlinkPartnerReferences(Long partnerId) {
        obstacleRepository.findByUser_Id(lookup.userId()).stream()
                .filter(obstacle -> obstacle.getRequiredPartner() != null
                        && partnerId.equals(obstacle.getRequiredPartner().getId()))
                .forEach(obstacle -> obstacle.setRequiredPartner(null));
        communicationMessageRepository.findByUser_Id(lookup.userId()).stream()
                .filter(message -> message.getPartner() != null
                        && partnerId.equals(message.getPartner().getId()))
                .forEach(message -> message.setPartner(null));
    }
}
