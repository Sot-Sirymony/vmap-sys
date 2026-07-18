package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.dto.request.VisionStepRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.VisionStepResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.ArchiveCascade;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PermanentDeleteCascade;
import com.visionmapping.service.support.ProgressCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The action stages of a goal. A complex step must own at least one task before
 * it can be completed; every change rolls progress up into its goal.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class VisionStepService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final EntityLookup lookup;
    private final ProgressCalculator progress;
    private final ArchiveCascade archiveCascade;
    private final PermanentDeleteCascade permanentDeleteCascade;
    private final VisionMappingMapper mapper;
    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;

    @Transactional(readOnly = true)
    public List<VisionStepResponse> listSteps(boolean includeArchived) {
        return findAllForUser(visionStepRepository, lookup.userId(), includeArchived).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public VisionStepResponse createStep(VisionStepRequest request) {
        AppUser user = lookup.currentUser();
        Goal goal = lookup.goal(request.goalId());
        VisionStep entity = VisionStep.builder()
                .code(nextCode("S", visionStepRepository.findByUser_Id(user.getId()), VisionStep::getCode))
                .user(user)
                .goal(goal)
                .title(request.title())
                .description(request.description())
                .sequenceNumber(request.sequenceNumber())
                .complex(request.complex())
                .priority(request.priority())
                .targetDate(request.targetDate())
                .status(request.status())
                .progressPercent(ZERO)
                .manualProgressOverride(false)
                .build();
        VisionStep saved = visionStepRepository.save(entity);
        progress.recalculateGoal(goal);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public VisionStepResponse getStep(Long id) {
        return mapper.toResponse(lookup.step(id));
    }

    public VisionStepResponse updateStep(Long id, VisionStepRequest request) {
        VisionStep entity = lookup.step(id);
        Goal oldGoal = entity.getGoal();
        entity.setGoal(lookup.goal(request.goalId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setSequenceNumber(request.sequenceNumber());
        entity.setComplex(request.complex());
        entity.setPriority(request.priority());
        entity.setTargetDate(request.targetDate());
        entity.setStatus(request.status());
        validateComplexStep(entity);
        progress.recalculateGoal(oldGoal);
        progress.recalculateGoal(entity.getGoal());
        return mapper.toResponse(entity);
    }

    public VisionStepResponse updateStepStatus(Long id, String status, boolean manualOverride) {
        VisionStep entity = lookup.step(id);
        entity.setStatus(parseEnum(WorkStatus.class, status));
        validateComplexStep(entity);
        if (manualOverride) {
            entity.setManualProgressOverride(true);
        }
        progress.recalculateGoal(entity.getGoal());
        return mapper.toResponse(entity);
    }

    public void archiveStep(Long id) {
        VisionStep entity = lookup.step(id);
        entity.setArchived(true);
        archiveCascade.archiveTasksUnder(entity.getId());
        progress.recalculateGoal(entity.getGoal());
    }

    @Transactional(readOnly = true)
    public ArchiveImpactResponse stepArchiveImpact(Long id) {
        return archiveCascade.impactOfStep(lookup.step(id));
    }

    public void restoreStep(Long id) {
        VisionStep entity = lookup.step(id);
        archiveCascade.unarchiveGoalChain(entity.getGoal());
        entity.setArchived(false);
        // BR-13: the step's ideal partner profile follows the step back.
        archiveCascade.setProfileArchived(entity.getId(), false);
        progress.recalculateGoal(entity.getGoal());
    }

    public void permanentlyDeleteStep(Long id) {
        VisionStep step = lookup.step(id);
        requireArchived(step.isArchived(), "Step");
        permanentDeleteCascade.deleteStep(step);
    }

    private void validateComplexStep(VisionStep step) {
        if (step.isComplex() && step.getStatus() == WorkStatus.COMPLETED
                && taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(step.getId(), step.getUser().getId()).isEmpty()) {
            throw new BusinessRuleException("A complex step must have at least one task before it can be completed.");
        }
    }
}
