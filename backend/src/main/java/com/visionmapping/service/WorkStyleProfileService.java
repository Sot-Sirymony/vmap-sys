package com.visionmapping.service;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.WorkStyleAssessmentRequest;
import com.visionmapping.dto.response.WorkStyleProfileResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.WorkStyleArchetype;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.util.UserScope;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-49: reads and (re)computes the authenticated user's work-style profile.
 *
 * <p>BR-38 shapes this class the same way BR-33 shapes
 * {@link AppearancePreferenceService}: scoping is absolute (every operation
 * goes through {@link UserScope#currentUser()}), and the profile is
 * diagnostic metadata that never blocks any other save.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class WorkStyleProfileService {

    /**
     * A margin of 2 or less (an 8-item axis splitting 4-4 or 5-3) counts as
     * "close" — the axis that's genuinely undecided, not the one that's
     * merely less lopsided than the other.
     */
    private static final int SECONDARY_MARGIN_THRESHOLD = 2;

    private final UserScope userScope;
    private final AppUserRepository appUserRepository;

    @Cacheable(CacheConfig.WORK_STYLE_PROFILE_CACHE)
    @Transactional(readOnly = true)
    public WorkStyleProfileResponse getMyProfile() {
        return toResponse(userScope.currentUser());
    }

    /**
     * FR-49.1: retaking overwrites the prior result outright — this is a
     * full replace, not a partial update like appearance preferences.
     */
    public WorkStyleProfileResponse submitAssessment(WorkStyleAssessmentRequest request) {
        AppUser user = userScope.currentUser();
        int paceFast = request.paceFastScore();
        int paceDeliberate = request.paceDeliberateScore();
        int focusTask = request.focusTaskScore();
        int focusPeople = request.focusPeopleScore();

        user.setWorkStylePaceFastScore(paceFast);
        user.setWorkStylePaceDeliberateScore(paceDeliberate);
        user.setWorkStyleFocusTaskScore(focusTask);
        user.setWorkStyleFocusPeopleScore(focusPeople);
        user.setWorkStyleDominant(computeDominant(paceFast, paceDeliberate, focusTask, focusPeople));
        user.setWorkStyleSecondary(computeSecondary(paceFast, paceDeliberate, focusTask, focusPeople));

        return toResponse(appUserRepository.save(user));
    }

    static WorkStyleArchetype computeDominant(int paceFast, int paceDeliberate, int focusTask, int focusPeople) {
        return archetypeOf(paceFast >= paceDeliberate, focusTask >= focusPeople);
    }

    /**
     * Flips whichever axis has the smaller margin (a tie between the two
     * margins flips pace, an arbitrary but deterministic choice) — provided
     * that smaller margin is itself within {@link #SECONDARY_MARGIN_THRESHOLD}.
     * A user who reads decisively on both axes gets no secondary at all.
     */
    static WorkStyleArchetype computeSecondary(int paceFast, int paceDeliberate, int focusTask, int focusPeople) {
        int marginPace = Math.abs(paceFast - paceDeliberate);
        int marginFocus = Math.abs(focusTask - focusPeople);
        if (Math.min(marginPace, marginFocus) > SECONDARY_MARGIN_THRESHOLD) {
            return null;
        }
        boolean fast = paceFast >= paceDeliberate;
        boolean task = focusTask >= focusPeople;
        return marginPace <= marginFocus ? archetypeOf(!fast, task) : archetypeOf(fast, !task);
    }

    private static WorkStyleArchetype archetypeOf(boolean fast, boolean task) {
        if (fast) {
            return task ? WorkStyleArchetype.DRIVER : WorkStyleArchetype.CONNECTOR;
        }
        return task ? WorkStyleArchetype.PLANNER : WorkStyleArchetype.STEADIER;
    }

    private static WorkStyleProfileResponse toResponse(AppUser user) {
        return new WorkStyleProfileResponse(
                user.getWorkStyleDominant(),
                user.getWorkStyleSecondary(),
                user.getWorkStylePaceFastScore(),
                user.getWorkStylePaceDeliberateScore(),
                user.getWorkStyleFocusTaskScore(),
                user.getWorkStyleFocusPeopleScore());
    }
}
