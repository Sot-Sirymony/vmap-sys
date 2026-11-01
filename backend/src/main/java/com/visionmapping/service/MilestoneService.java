package com.visionmapping.service;

import com.visionmapping.dto.request.MilestoneRequest;
import com.visionmapping.dto.response.MilestoneResponse;
import com.visionmapping.entity.Milestone;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.MilestoneRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-74: results reached on the way to a dream. */
@Service
@Transactional
@RequiredArgsConstructor
public class MilestoneService {

    private final EntityLookup lookup;
    private final MilestoneRepository milestoneRepository;

    @Transactional(readOnly = true)
    public List<MilestoneResponse> listForDream(Long dreamId) {
        lookup.dream(dreamId);
        return milestoneRepository.findByDream_IdAndUser_IdAndArchivedFalseOrderByAchievedOnDesc(dreamId, lookup.userId()).stream()
                .map(MilestoneService::toResponse)
                .toList();
    }

    public MilestoneResponse create(MilestoneRequest request) {
        Milestone entity = Milestone.builder()
                .user(lookup.currentUser())
                .dream(lookup.dream(request.dreamId()))
                .title(request.title())
                .achievedOn(request.achievedOn())
                .note(request.note())
                .build();
        return toResponse(milestoneRepository.save(entity));
    }

    public void archive(Long id) {
        milestoneRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + id))
                .setArchived(true);
    }

    private static MilestoneResponse toResponse(Milestone entity) {
        return new MilestoneResponse(entity.getId(), entity.getDream().getId(), entity.getTitle(), entity.getAchievedOn(), entity.getNote());
    }
}
