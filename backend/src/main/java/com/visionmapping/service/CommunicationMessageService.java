package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.dto.request.CommunicationMessageRequest;
import com.visionmapping.dto.response.CommunicationMessageResponse;
import com.visionmapping.entity.CommunicationMessage;
import com.visionmapping.entity.enums.CommunicationStatus;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.service.support.EntityLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public Page<CommunicationMessageResponse> listCommunicationMessages(
            Pageable pageable, boolean includeArchived, String search) {
        String term = search == null ? "" : search.trim();
        Page<CommunicationMessage> entities;
        if (term.isEmpty()) {
            entities = includeArchived
                    ? communicationMessageRepository.findByUser_Id(lookup.userId(), pageable)
                    : communicationMessageRepository.findByUser_IdAndArchivedFalse(lookup.userId(), pageable);
        } else {
            entities = communicationMessageRepository.search(
                    lookup.userId(), includeArchived, "%" + term.toLowerCase() + "%", pageable);
        }
        return entities.map(mapper::toResponse);
    }

    public CommunicationMessageResponse createCommunicationMessage(CommunicationMessageRequest request) {
        CommunicationMessage entity = new CommunicationMessage();
        entity.setUser(lookup.currentUser());
        applyCommunicationRequest(entity, request);
        return mapper.toResponse(communicationMessageRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public CommunicationMessageResponse getCommunicationMessage(Long id) {
        return mapper.toResponse(lookup.communicationMessage(id));
    }

    public CommunicationMessageResponse updateCommunicationMessage(Long id, CommunicationMessageRequest request) {
        CommunicationMessage entity = lookup.communicationMessage(id);
        applyCommunicationRequest(entity, request);
        return mapper.toResponse(entity);
    }

    public CommunicationMessageResponse updateCommunicationStatus(Long id, String status) {
        CommunicationMessage entity = lookup.communicationMessage(id);
        entity.setStatus(parseEnum(CommunicationStatus.class, status));
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
        entity.setMessageBody(request.messageBody());
        entity.setStatus(request.status());
        entity.setFollowUpDate(request.followUpDate());
    }
}
