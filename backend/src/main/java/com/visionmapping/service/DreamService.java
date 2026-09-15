package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.DreamRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.DreamResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.entity.enums.ScheduleMode;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.service.support.ArchiveCascade;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PermanentDeleteCascade;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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
    private final GoalRepository goalRepository;
    private final PartnerRepository partnerRepository;
    private final Clock clock;

    @Cacheable(CacheConfig.DREAM_LIST_CACHE)
    @Transactional(readOnly = true)
    public List<DreamResponse> listDreams(boolean includeArchived) {
        return findAllForUser(dreamRepository, lookup.userId(), includeArchived).stream()
                .map(this::toResponse)
                .toList();
    }

    public DreamResponse createDream(DreamRequest request) {
        AppUser user = lookup.currentUser();
        VisionArea visionArea = lookup.visionArea(request.visionAreaId());
        Dream entity = Dream.builder()
                .code(nextCode("D", dreamRepository.findByUser_Id(user.getId()), Dream::getCode))
                .user(user)
                .visionArea(visionArea)
                .title(request.title())
                .description(request.description())
                .whyImportant(request.whyImportant())
                .successDefinition(request.successDefinition())
                .dreamType(request.dreamType())
                .priority(request.priority())
                .letterRank(request.letterRank())
                .targetDate(request.targetDate())
                .status(request.status())
                .moonshot(request.moonshot())
                .moonshotVision(request.moonshotVision())
                .imageUrl(request.imageUrl())
                .scheduleMode(request.scheduleMode())
                .decisionSkippedResearch(request.decisionSkippedResearch())
                .decisionAssumedNoChange(request.decisionAssumedNoChange())
                .decisionTrustedUnverifiedClaim(request.decisionTrustedUnverifiedClaim())
                .decisionJudgedByAppearance(request.decisionJudgedByAppearance())
                .decisionUnderTimePressure(request.decisionUnderTimePressure())
                .decisionNoOutsideInput(request.decisionNoOutsideInput())
                .decisionChasedEasyReward(request.decisionChasedEasyReward())
                .decisionDismissedDisagreeingAdvice(request.decisionDismissedDisagreeingAdvice())
                .build();
        validateDecisionGate(entity);
        return toResponse(dreamRepository.save(entity));
    }

    @Cacheable(CacheConfig.DREAM_CACHE)
    @Transactional(readOnly = true)
    public DreamResponse getDream(Long id) {
        return toResponse(lookup.dream(id));
    }

    /** FR-59.4: backs the completion-time contribution nudge — never gates anything (BR-48). */
    @Transactional(readOnly = true)
    public boolean hasLinkedPartner(Long id) {
        Dream entity = lookup.dream(id);
        return partnerRepository.existsAnyPartnerForDream(entity.getUser().getId(), entity.getId());
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
        entity.setLetterRank(request.letterRank());
        entity.setTargetDate(request.targetDate());
        entity.setStatus(request.status());
        entity.setMoonshot(request.moonshot());
        entity.setMoonshotVision(request.moonshotVision());
        entity.setImageUrl(request.imageUrl());
        entity.setScheduleMode(request.scheduleMode());
        entity.setDecisionSkippedResearch(request.decisionSkippedResearch());
        entity.setDecisionAssumedNoChange(request.decisionAssumedNoChange());
        entity.setDecisionTrustedUnverifiedClaim(request.decisionTrustedUnverifiedClaim());
        entity.setDecisionJudgedByAppearance(request.decisionJudgedByAppearance());
        entity.setDecisionUnderTimePressure(request.decisionUnderTimePressure());
        entity.setDecisionNoOutsideInput(request.decisionNoOutsideInput());
        entity.setDecisionChasedEasyReward(request.decisionChasedEasyReward());
        entity.setDecisionDismissedDisagreeingAdvice(request.decisionDismissedDisagreeingAdvice());
        validateScheduleCascade(entity);
        validateDecisionGate(entity);
        return toResponse(entity);
    }

    public DreamResponse updateDreamStatus(Long id, String status) {
        Dream entity = lookup.dream(id);
        entity.setStatus(parseEnum(DreamStatus.class, status));
        validateDecisionGate(entity);
        return toResponse(entity);
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

    // FR-51: the latest targetDate among this dream's own non-archived goals
    // that actually have one set (a goal with no date yet never counts).
    private LocalDate latestActiveGoalDate(Dream dream) {
        return goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(dream.getId(), dream.getUser().getId()).stream()
                .map(Goal::getTargetDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    // BR-40: unless TOP_DOWN_FIXED, a dream's target date must not precede
    // its latest active goal's target date. Only runs where targetDate is
    // actually set on both sides — a blank date on either side skips the
    // check rather than forcing one in.
    private void validateScheduleCascade(Dream dream) {
        if (dream.getTargetDate() == null || dream.getScheduleMode() != ScheduleMode.BOTTOM_UP) {
            return;
        }
        LocalDate latestGoalDate = latestActiveGoalDate(dream);
        if (latestGoalDate != null && dream.getTargetDate().isBefore(latestGoalDate)) {
            throw new BusinessRuleException(
                    "This dream's target date (%s) is earlier than one of its goals' target date (%s). Move the dream's date later, adjust the goal, or switch this dream to a fixed top-down deadline."
                            .formatted(dream.getTargetDate(), latestGoalDate));
        }
    }

    /**
     * BR-44 / FR-55: a Moonshot dream with High/Critical priority needs one
     * of two gates cleared before it can be Active — either the eight-item
     * checklist fully answered (Gate A), or at least two Advisor/Mentor
     * partners linked to the dream or one of its goals (Gate B). Applies
     * whenever the entity's status is *becoming* ACTIVE (create or update),
     * not only on a literal Idea→Active transition — this closes the
     * loophole of creating the dream directly as Active, and mirrors
     * {@code PartnerService.prepareForActive}'s "check the entity's
     * current state" shape. Once cleared, {@code decisionGateClearedAt}
     * makes the gate permanently one-time for this dream (FR-55.4).
     */
    private void validateDecisionGate(Dream entity) {
        boolean needsGate = entity.getStatus() == DreamStatus.ACTIVE
                && entity.isMoonshot()
                && (entity.getPriority() == Priority.HIGH || entity.getPriority() == Priority.CRITICAL)
                && entity.getDecisionGateClearedAt() == null;
        if (!needsGate) {
            return;
        }
        if (!checklistComplete(entity) && !hasTwoCounselors(entity)) {
            throw new BusinessRuleException(
                    "This is a high-priority moonshot dream. Before moving it to Active, either complete the eight-item decision checklist, or link at least two Advisor/Mentor partners to it (directly or via a goal).");
        }
        entity.setDecisionGateClearedAt(Instant.now(clock));
    }

    private boolean checklistComplete(Dream entity) {
        return entity.getDecisionSkippedResearch() != null
                && entity.getDecisionAssumedNoChange() != null
                && entity.getDecisionTrustedUnverifiedClaim() != null
                && entity.getDecisionJudgedByAppearance() != null
                && entity.getDecisionUnderTimePressure() != null
                && entity.getDecisionNoOutsideInput() != null
                && entity.getDecisionChasedEasyReward() != null
                && entity.getDecisionDismissedDisagreeingAdvice() != null;
    }

    private boolean hasTwoCounselors(Dream entity) {
        // A brand-new dream (no id yet, mid-createDream) cannot already have
        // a partner linked to it, so there is nothing to query.
        if (entity.getId() == null) {
            return false;
        }
        List<Partner> counselors = partnerRepository.findCounselorsForDream(entity.getUser().getId(), entity.getId(),
                List.of(PartnerSupportType.ADVISOR, PartnerSupportType.MENTOR));
        return counselors.size() >= 2;
    }

    // FR-51: overrun is informational only — true when TOP_DOWN_FIXED lets a
    // parent date stand even though a child's date now runs past it.
    private DreamResponse toResponse(Dream entity) {
        LocalDate latestGoalDate = latestActiveGoalDate(entity);
        boolean overrun = entity.getScheduleMode() == ScheduleMode.TOP_DOWN_FIXED
                && entity.getTargetDate() != null && latestGoalDate != null
                && entity.getTargetDate().isBefore(latestGoalDate);
        String detail = overrun
                ? "A goal's target date (%s) is after this dream's fixed target date (%s).".formatted(latestGoalDate, entity.getTargetDate())
                : null;
        return mapper.toResponse(entity, overrun, detail);
    }
}
