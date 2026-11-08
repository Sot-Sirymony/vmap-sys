package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.IdealPartnerProfileRequest;
import com.visionmapping.dto.response.IdealPartnerProfileResponse;
import com.visionmapping.entity.IdealPartnerProfile;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.IdealPartnerProfileRepository;
import com.visionmapping.service.support.ArchiveFilter;
import com.visionmapping.service.support.EntityLookup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The partner a step needs, written down before anyone is recruited (FR-15.1).
 * A step has at most one profile; moving a profile to another step follows the
 * same one-per-step rule.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class IdealPartnerProfileService {

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final IdealPartnerProfileRepository idealPartnerProfileRepository;

    @Cacheable(CacheConfig.IDEAL_PARTNER_PROFILE_LIST_CACHE)
    @Transactional(readOnly = true)
    public List<IdealPartnerProfileResponse> listProfiles(ArchiveFilter archiveFilter) {
        return findAllForUser(idealPartnerProfileRepository, lookup.userId(), archiveFilter).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public IdealPartnerProfileResponse createProfile(IdealPartnerProfileRequest request) {
        VisionStep step = lookup.step(request.stepId());
        requireStepWithoutProfile(step.getId());
        IdealPartnerProfile entity = IdealPartnerProfile.builder()
                .user(lookup.currentUser())
                .step(step)
                .requiredExperience(request.requiredExperience())
                .characterTraits(request.characterTraits())
                .motivation(request.motivation())
                .offerInReturn(request.offerInReturn())
                .build();
        return mapper.toResponse(idealPartnerProfileRepository.save(entity));
    }

    @Cacheable(CacheConfig.IDEAL_PARTNER_PROFILE_CACHE)
    @Transactional(readOnly = true)
    public IdealPartnerProfileResponse getProfile(Long id) {
        return mapper.toResponse(profile(id));
    }

    public IdealPartnerProfileResponse updateProfile(Long id, IdealPartnerProfileRequest request) {
        IdealPartnerProfile entity = profile(id);
        if (!entity.getStep().getId().equals(request.stepId())) {
            requireStepWithoutProfile(request.stepId());
            entity.setStep(lookup.step(request.stepId()));
        }
        entity.setRequiredExperience(request.requiredExperience());
        entity.setCharacterTraits(request.characterTraits());
        entity.setMotivation(request.motivation());
        entity.setOfferInReturn(request.offerInReturn());
        return mapper.toResponse(entity);
    }

    public void archiveProfile(Long id) {
        profile(id).setArchived(true);
    }

    public void restoreProfile(Long id) {
        profile(id).setArchived(false);
    }

    public void permanentlyDeleteProfile(Long id) {
        IdealPartnerProfile entity = profile(id);
        requireArchived(entity.isArchived(), "Ideal partner profile");
        idealPartnerProfileRepository.delete(entity);
    }

    private IdealPartnerProfile profile(Long id) {
        return idealPartnerProfileRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Ideal partner profile not found: " + id));
    }

    private void requireStepWithoutProfile(Long stepId) {
        if (idealPartnerProfileRepository.findByStep_IdAndUser_Id(stepId, lookup.userId()).isPresent()) {
            throw new BusinessRuleException("This step already has an ideal partner profile. Edit the existing one instead.");
        }
    }
}
