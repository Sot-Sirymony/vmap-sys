package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.request.ObstacleRequest;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.ProgressLogRepository;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionAreaRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.util.UserScope;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * FR-32 "creative persistence": Resolved requires a root cause (BR-25) and
 * Accepted requires at least three brainstormed alternatives (BR-26), both
 * enforced on every path that can set status.
 */
@ExtendWith(MockitoExtension.class)
class ObstacleServiceTest {

    @Mock private UserScope userScope;
    @Mock private VisionAreaRepository visionAreaRepository;
    @Mock private DreamRepository dreamRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private VisionStepRepository visionStepRepository;
    @Mock private TaskItemRepository taskItemRepository;
    @Mock private PartnerRepository partnerRepository;
    @Mock private CommunicationMessageRepository communicationMessageRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private ProgressLogRepository progressLogRepository;
    @Mock private ObstacleRepository obstacleRepository;

    private ObstacleService service;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        EntityLookup lookup = new EntityLookup(userScope, visionAreaRepository, dreamRepository, goalRepository,
                visionStepRepository, taskItemRepository, partnerRepository, communicationMessageRepository,
                reviewRepository, obstacleRepository, progressLogRepository);
        service = new ObstacleService(lookup, new VisionMappingMapper(), obstacleRepository);

        testUser = AppUser.builder()
                .id(1L)
                .fullName("Test User")
                .email("test@example.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        lenient().when(userScope.currentUser()).thenReturn(testUser);
    }

    private ObstacleRequest request(ObstacleStatus status, String rootCause, String creativeAlternatives) {
        return new ObstacleRequest(null, null, null, null, "Stalled progress", null,
                ObstacleType.TIME, Severity.MEDIUM, "worked around it for now", rootCause, creativeAlternatives,
                null, status);
    }

    private Obstacle existing(ObstacleStatus status) {
        return Obstacle.builder().id(10L).user(testUser).title("Stalled progress")
                .obstacleType(ObstacleType.TIME).severity(Severity.MEDIUM).status(status).archived(false).build();
    }

    @Test
    void creatingResolvedObstacleWithoutRootCauseThrows() {
        assertThatThrownBy(() -> service.createObstacle(request(ObstacleStatus.RESOLVED, "   ", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Resolved obstacles must include a root cause.");
    }

    @Test
    void creatingResolvedObstacleWithRootCauseSucceeds() {
        when(obstacleRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObstacleResponse response = service.createObstacle(
                request(ObstacleStatus.RESOLVED, "Underestimated the timeline", null));

        assertThat(response.status()).isEqualTo(ObstacleStatus.RESOLVED);
        assertThat(response.rootCause()).isEqualTo("Underestimated the timeline");
    }

    @Test
    void creatingAcceptedObstacleWithFewerThanThreeAlternativesThrows() {
        assertThatThrownBy(() -> service.createObstacle(
                request(ObstacleStatus.ACCEPTED, null, "Ask a mentor\n\nDelay the deadline")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Accepted obstacles must include at least 3 creative alternatives.");
    }

    @Test
    void creatingAcceptedObstacleWithThreeAlternativesSucceeds() {
        when(obstacleRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObstacleResponse response = service.createObstacle(request(ObstacleStatus.ACCEPTED, null,
                "Ask a mentor\nDelay the deadline\nSplit the task with a partner"));

        assertThat(response.status()).isEqualTo(ObstacleStatus.ACCEPTED);
    }

    @Test
    void openAndInProgressObstaclesNeedNeitherField() {
        when(obstacleRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObstacleResponse response = service.createObstacle(request(ObstacleStatus.OPEN, null, null));

        assertThat(response.status()).isEqualTo(ObstacleStatus.OPEN);
    }

    @Test
    void updatingToResolvedWithoutRootCauseThrows() {
        when(obstacleRepository.findById(10L)).thenReturn(Optional.of(existing(ObstacleStatus.OPEN)));

        assertThatThrownBy(() -> service.updateObstacle(10L, request(ObstacleStatus.RESOLVED, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Resolved obstacles must include a root cause.");
    }

    @Test
    void quickStatusChangeToAcceptedEnforcesTheSameGuard() {
        Obstacle stored = existing(ObstacleStatus.OPEN);
        when(obstacleRepository.findById(10L)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.updateObstacleStatus(10L, "ACCEPTED"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Accepted obstacles must include at least 3 creative alternatives.");
    }

    @Test
    void quickStatusChangeToResolvedSucceedsWhenRootCauseAlreadyPresent() {
        Obstacle stored = existing(ObstacleStatus.OPEN);
        stored.setRootCause("Underestimated the timeline");
        when(obstacleRepository.findById(10L)).thenReturn(Optional.of(stored));

        ObstacleResponse response = service.updateObstacleStatus(10L, "RESOLVED");

        assertThat(response.status()).isEqualTo(ObstacleStatus.RESOLVED);
    }
}
