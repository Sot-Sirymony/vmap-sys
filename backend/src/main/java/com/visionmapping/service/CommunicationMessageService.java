package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.likeTerm;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.CommunicationMessageRequest;
import com.visionmapping.dto.response.CommunicationMessageResponse;
import com.visionmapping.entity.CommunicationMessage;
import com.visionmapping.entity.enums.CommunicationStatus;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.service.support.ArchiveFilter;
import com.visionmapping.service.support.EntityLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.visionmapping.dto.response.PagedResponse;
import com.visionmapping.util.Pageables;
import java.util.Set;
import java.util.function.Function;

/**
 * Structured outreach messages to partners (audience, hook, problem, request,
 * benefit, word picture) with their draft/sent lifecycle.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CommunicationMessageService {

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final CommunicationMessageRepository communicationMessageRepository;

    /** V33 (D-33-10): the message table's sortable columns, and dates. */
    static final Set<String> MESSAGE_SORTS = Set.of("subject", "audience", "purpose", "status", "followUpDate", "createdAt", "updatedAt");

    /** V33 (D-33-9): one page of messages, in the shared page shape, with the sort checked first. */
    @Transactional(readOnly = true)
    public PagedResponse<CommunicationMessageResponse> pageCommunicationMessages(
            Pageable pageable,
            ArchiveFilter archiveFilter,
            @Nullable String search,
            @Nullable Long partnerId,
            @Nullable CommunicationStatus status) {
        return PagedResponse.from(listCommunicationMessages(Pageables.whitelist(pageable, MESSAGE_SORTS), archiveFilter, search, partnerId, status),
                Function.identity());
    }

    @Transactional(readOnly = true)
    public Page<CommunicationMessageResponse> listCommunicationMessages(
            Pageable pageable, ArchiveFilter archiveFilter, String search) {
        return listCommunicationMessages(pageable, archiveFilter, search, null, null);
    }

    @Transactional(readOnly = true)
    public Page<CommunicationMessageResponse> listCommunicationMessages(
            Pageable pageable,
            ArchiveFilter archiveFilter,
            String search,
            @Nullable Long partnerId,
            @Nullable CommunicationStatus status) {
        return communicationMessageRepository
                .findFiltered(lookup.userId(), archiveFilter.includesArchived(), partnerId, status, likeTerm(search).orElse(null), pageable)
                .map(mapper::toResponse);
    }

    public CommunicationMessageResponse createCommunicationMessage(CommunicationMessageRequest request) {
        CommunicationMessage entity = new CommunicationMessage();
        entity.setUser(lookup.currentUser());
        applyCommunicationRequest(entity, request);
        return mapper.toResponse(communicationMessageRepository.save(entity));
    }

    @Cacheable(CacheConfig.COMMUNICATION_MESSAGE_CACHE)
    @Transactional(readOnly = true)
    public CommunicationMessageResponse getCommunicationMessage(Long id) {
        return mapper.toResponse(lookup.communicationMessage(id));
    }

    public CommunicationMessageResponse updateCommunicationMessage(Long id, CommunicationMessageRequest request) {
        CommunicationMessage entity = lookup.communicationMessage(id);
        applyCommunicationRequest(entity, request);
        return mapper.toResponse(entity);
    }

    public CommunicationMessageResponse updateCommunicationStatus(Long id, CommunicationStatus status) {
        CommunicationMessage entity = lookup.communicationMessage(id);
        entity.setStatus(status);
        return mapper.toResponse(entity);
    }

    public void archiveCommunicationMessage(Long id) {
        lookup.communicationMessage(id).setArchived(true);
    }

    public void restoreCommunicationMessage(Long id) {
        lookup.communicationMessage(id).setArchived(false);
    }

    public void permanentlyDeleteCommunicationMessage(Long id) {
        CommunicationMessage message = lookup.communicationMessage(id);
        requireArchived(message.isArchived(), "Communication message");
        communicationMessageRepository.delete(message);
    }

    private void applyCommunicationRequest(CommunicationMessage entity, CommunicationMessageRequest request) {
        entity.setPartner(lookup.optionalPartner(request.partnerId()));
        entity.setRelatedDream(lookup.optionalDream(request.relatedDreamId()));
        entity.setRelatedGoal(lookup.optionalGoal(request.relatedGoalId()));
        entity.setRelatedTask(lookup.optionalTask(request.relatedTaskId()));
        entity.setAudience(request.audience());
        entity.setPurpose(request.purpose());
        entity.setSubject(request.subject());
        entity.setHook(request.hook());
        entity.setProblem(request.problem());
        entity.setRequest(request.request());
        entity.setBenefitToPartner(request.benefitToPartner());
        entity.setWordPicture(request.wordPicture());
        entity.setExpectedOutcome(request.expectedOutcome());
        entity.setObjectionsAndAnswers(request.objectionsAndAnswers());
        entity.setSocialProof(request.socialProof());
        entity.setValueComparison(request.valueComparison());
        entity.setCallToAction(request.callToAction());
        entity.setMessageBody(request.messageBody());
        entity.setStatus(request.status());
        entity.setFollowUpDate(request.followUpDate());
    }
}
