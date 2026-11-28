package com.visionmapping.service;

import com.visionmapping.dto.request.HabitRequest;
import com.visionmapping.dto.request.InnerBlockerHabitRequest;
import com.visionmapping.dto.request.InnerBlockerRequest;
import com.visionmapping.dto.request.InnerBlockerStatusRequest;
import com.visionmapping.dto.response.InnerBlockerResponse;
import com.visionmapping.dto.response.PagedResponse;
import com.visionmapping.entity.Habit;
import com.visionmapping.entity.InnerBlocker;
import com.visionmapping.entity.InnerBlockerCheckin;
import com.visionmapping.entity.enums.InnerBlockerAnswer;
import com.visionmapping.entity.enums.InnerBlockerStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.exception.StateConflictException;
import com.visionmapping.repository.HabitCheckRepository;
import com.visionmapping.repository.HabitRepository;
import com.visionmapping.repository.InnerBlockerCheckinRepository;
import com.visionmapping.repository.InnerBlockerRepository;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.util.Pageables;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V34 (BRD 34): the participant's own inner blockers. Every lookup is scoped
 * to the caller, so another user's blocker answers 404 (BR-34-1). Nothing here
 * calls the AI (D-34-12), and nothing is deleted except with the account (BR-12).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class InnerBlockerService {

    /** BR-34-3: at most this many active blockers per participant. */
    static final int MAX_ACTIVE = 10;
    /** D-34-10: this many No answers in a row mean the practice isn't helping yet. */
    static final int NOT_HELPING_AFTER = 2;
    /** V33 (D-33-10): the properties the list can be sorted by. */
    static final Set<String> SORTS = Set.of("createdAt", "updatedAt");

    private final EntityLookup lookup;
    private final InnerBlockerRepository repository;
    private final InnerBlockerCheckinRepository checkinRepository;
    private final HabitService habitService;
    private final HabitRepository habitRepository;
    private final HabitCheckRepository habitCheckRepository;
    private final Clock clock;

    /** §2.1, §2.2: one page of the caller's blockers. A null status means any status. */
    @Transactional(readOnly = true)
    public PagedResponse<InnerBlockerResponse> page(@Nullable InnerBlockerStatus status, boolean archived,
                                                    @Nullable Long dreamId, boolean includeAllDreams,
                                                    @Nullable LocalDate date, Pageable pageable) {
        return PagedResponse.from(repository.search(lookup.userId(), archived, status, dreamId, includeAllDreams,
                Pageables.whitelist(pageable, SORTS)), blocker -> toResponse(blocker, date));
    }

    @Transactional(readOnly = true)
    public InnerBlockerResponse get(Long id, @Nullable LocalDate date) {
        return toResponse(blocker(id), date);
    }

    public InnerBlockerResponse create(InnerBlockerRequest request) {
        requireRoomForAnother();
        InnerBlocker blocker = InnerBlocker.builder().user(lookup.currentUser()).build();
        apply(blocker, request);
        return toResponse(repository.save(blocker), null);
    }

    public InnerBlockerResponse update(Long id, InnerBlockerRequest request) {
        InnerBlocker blocker = blocker(id);
        apply(blocker, request);
        return toResponse(blocker, null);
    }

    /** D-34-7, BR-34-5: resolve or reopen; reopening needs room under the limit. */
    public InnerBlockerResponse changeStatus(Long id, InnerBlockerStatusRequest request) {
        InnerBlocker blocker = blocker(id);
        if (request.status() != blocker.getStatus()) {
            if (request.status() == InnerBlockerStatus.ACTIVE && !blocker.isArchived()) {
                requireRoomForAnother();
            }
            blocker.setStatus(request.status());
            blocker.setResolvedAt(request.status() == InnerBlockerStatus.RESOLVED ? Instant.now(clock) : null);
        }
        if (request.status() == InnerBlockerStatus.RESOLVED && request.stopHabit()) {
            stopHabit(blocker);
        }
        return toResponse(blocker, null);
    }

    /** BR-34-5: archiving keeps the habit unless the participant asked to stop it too. */
    public void archive(Long id, boolean stopHabit) {
        InnerBlocker blocker = blocker(id);
        blocker.setArchived(true);
        if (stopHabit) {
            stopHabit(blocker);
        }
    }

    public void restore(Long id) {
        InnerBlocker blocker = blocker(id);
        if (blocker.isArchived()) {
            if (blocker.getStatus() == InnerBlockerStatus.ACTIVE) {
                requireRoomForAnother();
            }
            blocker.setArchived(false);
        }
    }

    /** D-34-6: make the practice a daily habit (FR-68, at most three at once) and link it. */
    public InnerBlockerResponse makeHabit(Long id, InnerBlockerHabitRequest request, LocalDate date) {
        InnerBlocker blocker = blocker(id);
        if (blocker.isArchived() || blocker.getStatus() != InnerBlockerStatus.ACTIVE) {
            throw new BusinessRuleException("Only an active inner blocker can become a daily habit.");
        }
        if (blocker.getHabit() != null && !blocker.getHabit().isArchived()) {
            throw new BusinessRuleException("This practice is already a daily habit.");
        }
        Long habitId = habitService.createHabit(new HabitRequest(request.name().trim()), date).id();
        blocker.setHabit(habitRepository.getReferenceById(habitId));
        return toResponse(blocker, date);
    }

    /** D-34-11: a coach's read-only view. The caller has already checked the coach may see this participant. */
    @Transactional(readOnly = true)
    public List<InnerBlockerResponse> forParticipant(Long participantId) {
        return repository.findByUser_IdAndArchivedFalseOrderByCreatedAtDesc(participantId).stream()
                .map(blocker -> toResponse(blocker, null))
                .toList();
    }

    /** IB-FR-12: every blocker of the user, archived ones included, for the account export. */
    @Transactional(readOnly = true)
    public List<InnerBlockerResponse> forExport(Long userId) {
        return repository.findByUser_Id(userId).stream().map(blocker -> toResponse(blocker, null)).toList();
    }

    /** True after {@value #NOT_HELPING_AFTER} No answers in a row, newest first (D-34-10). */
    static boolean notHelping(List<InnerBlockerAnswer> newestFirst) {
        return newestFirst.size() >= NOT_HELPING_AFTER
                && newestFirst.subList(0, NOT_HELPING_AFTER).stream().allMatch(answer -> answer == InnerBlockerAnswer.NO);
    }

    private void apply(InnerBlocker blocker, InnerBlockerRequest request) {
        blocker.setBlocker(request.blocker().trim());
        blocker.setShowsUp(blankToNull(request.showsUp()));
        blocker.setPractice(request.practice().trim());
        blocker.setChainKey(blankToNull(request.chainKey()));
        blocker.setDream(lookup.optionalDream(request.dreamId()));
    }

    private void stopHabit(InnerBlocker blocker) {
        Habit habit = blocker.getHabit();
        if (habit != null && !habit.isArchived()) {
            habitService.archiveHabit(habit.getId());
        }
    }

    private void requireRoomForAnother() {
        if (repository.countByUser_IdAndArchivedFalseAndStatus(lookup.userId(), InnerBlockerStatus.ACTIVE) >= MAX_ACTIVE) {
            throw new StateConflictException("You have %d active inner blockers. Resolve or archive one first.".formatted(MAX_ACTIVE));
        }
    }

    private InnerBlockerResponse toResponse(InnerBlocker blocker, @Nullable LocalDate date) {
        List<InnerBlockerAnswer> answers = blocker.getId() == null ? List.of()
                : checkinRepository.findTop4ByInnerBlocker_IdOrderByReview_ReviewDateDescIdDesc(blocker.getId()).stream()
                        .map(InnerBlockerCheckin::getAnswer)
                        .toList();
        Habit habit = blocker.getHabit();
        Integer streak = habit == null || date == null ? null
                : HabitService.currentStreak(habitCheckRepository.findDatesByHabitId(habit.getId()), date);
        return new InnerBlockerResponse(blocker.getId(), blocker.getBlocker(), blocker.getShowsUp(), blocker.getPractice(),
                blocker.getChainKey(),
                blocker.getDream() == null ? null : blocker.getDream().getId(),
                blocker.getDream() == null ? null : blocker.getDream().getTitle(),
                habit == null ? null : habit.getId(),
                habit == null ? null : habit.getName(),
                habit != null && habit.isArchived(),
                streak, blocker.getStatus(), blocker.getResolvedAt(), blocker.isArchived(), answers, notHelping(answers),
                blocker.getCreatedAt(), blocker.getUpdatedAt());
    }

    private InnerBlocker blocker(Long id) {
        return repository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Inner blocker not found: " + id));
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
