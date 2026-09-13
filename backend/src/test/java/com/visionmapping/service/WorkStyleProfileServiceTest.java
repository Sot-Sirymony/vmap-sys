package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.visionmapping.dto.request.WorkStyleAssessmentRequest;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.entity.enums.WorkStyleArchetype;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.util.UserScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * FR-49.1: dominant/secondary archetype computation from the four raw axis
 * scores the frontend quiz produces.
 */
@ExtendWith(MockitoExtension.class)
class WorkStyleProfileServiceTest {

    @Mock private UserScope userScope;
    @Mock private AppUserRepository appUserRepository;

    private WorkStyleProfileService service;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        service = new WorkStyleProfileService(userScope, appUserRepository);
        testUser = AppUser.builder().id(1L).fullName("Test User").email("test@example.com")
                .passwordHash("hash").role(UserRole.USER).status(UserStatus.ACTIVE).build();
        lenient().when(userScope.currentUser()).thenReturn(testUser);
        lenient().when(appUserRepository.save(testUser)).thenReturn(testUser);
    }

    @Test
    void decisiveOnBothAxesYieldsDriverWithNoSecondary() {
        var response = service.submitAssessment(new WorkStyleAssessmentRequest(8, 0, 8, 0));

        assertThat(response.dominant()).isEqualTo(WorkStyleArchetype.DRIVER);
        assertThat(response.secondary()).isNull();
    }

    @Test
    void decisivePaceCloseFocusYieldsSecondaryFlippingFocus() {
        // pace 8-0 (decisive, margin 8), focus 5-3 (close, margin 2) -> dominant
        // DRIVER (fast+task), secondary flips focus only -> CONNECTOR (fast+people).
        var response = service.submitAssessment(new WorkStyleAssessmentRequest(8, 0, 5, 3));

        assertThat(response.dominant()).isEqualTo(WorkStyleArchetype.DRIVER);
        assertThat(response.secondary()).isEqualTo(WorkStyleArchetype.CONNECTOR);
    }

    @Test
    void closePaceDecisiveFocusYieldsSecondaryFlippingPace() {
        // pace 4-4 (close, margin 0), focus 8-0 (decisive) -> dominant PLANNER
        // (pace ties toward fast per the tie-break... wait: fast>=deliberate at
        // 4-4 is true, so dominant pace reads FAST) -> DRIVER; secondary flips
        // pace -> PLANNER (deliberate+task).
        var response = service.submitAssessment(new WorkStyleAssessmentRequest(4, 4, 8, 0));

        assertThat(response.dominant()).isEqualTo(WorkStyleArchetype.DRIVER);
        assertThat(response.secondary()).isEqualTo(WorkStyleArchetype.PLANNER);
    }

    @Test
    void bothAxesCloseFlipsPaceOnTieBreak() {
        // pace 4-4 (margin 0) and focus 4-4 (margin 0) are equally close;
        // the tie-break flips pace. Dominant: fast+task -> DRIVER. Secondary:
        // deliberate+task -> PLANNER.
        var response = service.submitAssessment(new WorkStyleAssessmentRequest(4, 4, 4, 4));

        assertThat(response.dominant()).isEqualTo(WorkStyleArchetype.DRIVER);
        assertThat(response.secondary()).isEqualTo(WorkStyleArchetype.PLANNER);
    }

    @Test
    void deliberateAndPeopleFocusedYieldsSteadier() {
        var response = service.submitAssessment(new WorkStyleAssessmentRequest(1, 7, 2, 6));

        assertThat(response.dominant()).isEqualTo(WorkStyleArchetype.STEADIER);
    }

    @Test
    void retakingOverwritesThePriorResult() {
        service.submitAssessment(new WorkStyleAssessmentRequest(8, 0, 8, 0));
        assertThat(service.getMyProfile().dominant()).isEqualTo(WorkStyleArchetype.DRIVER);

        service.submitAssessment(new WorkStyleAssessmentRequest(0, 8, 0, 8));
        var retaken = service.getMyProfile();

        assertThat(retaken.dominant()).isEqualTo(WorkStyleArchetype.STEADIER);
        assertThat(retaken.paceFastScore()).isEqualTo(0);
        assertThat(retaken.paceDeliberateScore()).isEqualTo(8);
    }

    @Test
    void neverTakenAssessmentReturnsNullProfile() {
        var response = service.getMyProfile();

        assertThat(response.dominant()).isNull();
        assertThat(response.secondary()).isNull();
        assertThat(response.paceFastScore()).isNull();
    }
}
