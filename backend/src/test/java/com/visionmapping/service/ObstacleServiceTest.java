package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.request.ObstacleRequest;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.ObstaclePlacement;
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
        service = new ObstacleService(lookup, new VisionMappingMapper(), obstacleRepository, java.time.Clock.systemDefaultZone());

        testUser = AppUser.builder()
                .id(1L)
                .fullName("Test User")
                .email("test@example.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        lenient().when(userScope.currentUser()).thenReturn(testUser);

        // V17: a small plan. Dream 1 → goal 2 → step 5 (task 7) and step 6.
        dream = Dream.builder().id(1L).user(testUser).build();
        otherDream = Dream.builder().id(9L).user(testUser).build();
        goal = Goal.builder().id(2L).user(testUser).dream(dream).build();
        step = VisionStep.builder().id(5L).user(testUser).goal(goal).build();
        otherStep = VisionStep.builder().id(6L).user(testUser).goal(goal).build();
        task = TaskItem.builder().id(7L).user(testUser).step(step).build();
        lenient().when(dreamRepository.findById(1L)).thenReturn(Optional.of(dream));
        lenient().when(dreamRepository.findById(9L)).thenReturn(Optional.of(otherDream));
        lenient().when(goalRepository.findById(2L)).thenReturn(Optional.of(goal));
        lenient().when(visionStepRepository.findById(5L)).thenReturn(Optional.of(step));
        lenient().when(visionStepRepository.findById(6L)).thenReturn(Optional.of(otherStep));
        lenient().when(taskItemRepository.findById(7L)).thenReturn(Optional.of(task));
        lenient().when(obstacleRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Dream dream;
    private Dream otherDream;
    private Goal goal;
    private VisionStep step;
    private VisionStep otherStep;
    private TaskItem task;

    /** A TIME obstacle on step 5 (V17 BL-FR-01). */
    private ObstacleRequest request(ObstacleStatus status, String rootCause, String creativeAlternatives) {
        return new ObstacleRequest(null, null, 5L, null, "Stalled progress", null,
                ObstacleType.TIME, Severity.MEDIUM, null, "worked around it for now", rootCause, creativeAlternatives,
                null, null, null, null, null, null,
                null, null,
                null, null, null, null,
                null, null, null,
                null, status);
    }

    private ObstacleRequest partnerRequest(ObstacleStatus status, String rootCause, Boolean noCharacterAttacks,
            Boolean stayedOnIncident, Boolean noThreatsOrSarcasm, Boolean definedWinWin) {
        return new ObstacleRequest(null, null, 5L, null, "Disagreement with a mentor", null,
                ObstacleType.PARTNER, Severity.MEDIUM, null, "worked around it for now", rootCause, null,
                null, null, null, null, null, null,
                null, null,
                noCharacterAttacks, stayedOnIncident, noThreatsOrSarcasm, definedWinWin,
                null, null, null,
                null, status);
    }

    private Obstacle existing(ObstacleStatus status) {
        return Obstacle.builder().id(10L).user(testUser).title("Stalled progress")
                .obstacleType(ObstacleType.TIME).severity(Severity.MEDIUM).status(status).archived(false).build();
    }

    private Obstacle partnerExisting(ObstacleStatus status) {
        return Obstacle.builder().id(11L).user(testUser).title("Disagreement with a mentor")
                .obstacleType(ObstacleType.PARTNER).severity(Severity.MEDIUM).status(status).archived(false).build();
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
    void relatedObstaclesReturnsOnlySameTypeResolvedOnes() {
        Obstacle target = existing(ObstacleStatus.OPEN); // id 10, type TIME
        Obstacle resolvedSameType = Obstacle.builder().id(20L).user(testUser).title("Old time issue")
                .obstacleType(ObstacleType.TIME).severity(Severity.MEDIUM).status(ObstacleStatus.RESOLVED).archived(false).build();
        Obstacle resolvedOtherType = Obstacle.builder().id(21L).user(testUser).title("Money issue")
                .obstacleType(ObstacleType.MONEY).severity(Severity.MEDIUM).status(ObstacleStatus.RESOLVED).archived(false).build();
        Obstacle openSameType = Obstacle.builder().id(22L).user(testUser).title("Still open")
                .obstacleType(ObstacleType.TIME).severity(Severity.MEDIUM).status(ObstacleStatus.OPEN).archived(false).build();

        when(obstacleRepository.findById(10L)).thenReturn(Optional.of(target));
        when(obstacleRepository.findByUser_IdAndArchivedFalse(1L))
                .thenReturn(java.util.List.of(target, resolvedSameType, resolvedOtherType, openSameType));

        assertThat(service.relatedObstacles(10L)).extracting(ObstacleResponse::id).containsExactly(20L);
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

        assertThatThrownBy(() -> service.updateObstacleStatus(10L, ObstacleStatus.ACCEPTED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Accepted obstacles must include at least 3 creative alternatives.");
    }

    @Test
    void quickStatusChangeToResolvedSucceedsWhenRootCauseAlreadyPresent() {
        Obstacle stored = existing(ObstacleStatus.OPEN);
        stored.setRootCause("Underestimated the timeline");
        when(obstacleRepository.findById(10L)).thenReturn(Optional.of(stored));

        ObstacleResponse response = service.updateObstacleStatus(10L, ObstacleStatus.RESOLVED);

        assertThat(response.status()).isEqualTo(ObstacleStatus.RESOLVED);
    }

    @Test
    void releasingAnUnreleasedExpectationStampsATimestamp() {
        Obstacle stored = existing(ObstacleStatus.OPEN);
        when(obstacleRepository.findById(10L)).thenReturn(Optional.of(stored));

        ObstacleResponse response = service.releaseExpectation(10L);

        assertThat(response.expectationReleasedAt()).isNotNull();
    }

    @Test
    void releasingAnAlreadyReleasedExpectationDoesNotReStampIt() {
        Obstacle stored = existing(ObstacleStatus.OPEN);
        var firstStamp = java.time.Instant.parse("2026-01-01T00:00:00Z");
        stored.setExpectationReleasedAt(firstStamp);
        when(obstacleRepository.findById(10L)).thenReturn(Optional.of(stored));

        ObstacleResponse response = service.releaseExpectation(10L);

        assertThat(response.expectationReleasedAt()).isEqualTo(firstStamp);
    }

    @Test
    void resolvingPartnerObstacleWithIncompleteChecklistThrows() {
        assertThatThrownBy(() -> service.createObstacle(
                partnerRequest(ObstacleStatus.RESOLVED, "Underestimated the timeline", true, true, true, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Resolved PARTNER obstacles must also complete all four conflict engagement checklist items.");
    }

    @Test
    void resolvingPartnerObstacleWithCompleteChecklistSucceedsEvenWithANoAnswer() {
        when(obstacleRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObstacleResponse response = service.createObstacle(
                partnerRequest(ObstacleStatus.RESOLVED, "Underestimated the timeline", true, false, true, true));

        assertThat(response.status()).isEqualTo(ObstacleStatus.RESOLVED);
    }

    @Test
    void resolvingNonPartnerObstacleIsUnaffectedByTheChecklist() {
        when(obstacleRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObstacleResponse response = service.createObstacle(
                request(ObstacleStatus.RESOLVED, "Underestimated the timeline", null));

        assertThat(response.status()).isEqualTo(ObstacleStatus.RESOLVED);
    }

    @Test
    void quickStatusChangeToResolvedEnforcesTheChecklistForPartnerObstacles() {
        Obstacle stored = partnerExisting(ObstacleStatus.OPEN);
        stored.setRootCause("Underestimated the timeline");
        stored.setConflictNoCharacterAttacks(true);
        stored.setConflictStayedOnIncident(true);
        stored.setConflictNoThreatsOrSarcasm(true);
        // conflictDefinedWinWin left unanswered
        when(obstacleRepository.findById(11L)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.updateObstacleStatus(11L, ObstacleStatus.RESOLVED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Resolved PARTNER obstacles must also complete all four conflict engagement checklist items.");
    }

    // ---------- V17 placement (BRD 17.0.0) ----------

    private ObstacleRequest linked(ObstacleType type, Long dreamId, Long goalId, Long stepId, Long taskId, String conflictIncident) {
        return new ObstacleRequest(dreamId, goalId, stepId, taskId, "No budget", null,
                type, Severity.HIGH, null, null, null, null,
                conflictIncident, null, null, null, null, null,
                null, null,
                null, null, null, null,
                null, null, null,
                null, ObstacleStatus.OPEN);
    }

    @Test
    void blc01_aPlanObstacleOnlyOnADreamIsRefused() {
        assertThatThrownBy(() -> service.createObstacle(linked(ObstacleType.KNOWLEDGE, 1L, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Put this blocker on a step or a task.");
    }

    @Test
    void blc02_onATaskTheStepGoalAndDreamComeFromTheTaskNotTheRequest() {
        ObstacleResponse response = service.createObstacle(linked(ObstacleType.MONEY, 9L, null, null, 7L, null));

        assertThat(response.relatedTaskId()).isEqualTo(7L);
        assertThat(response.relatedStepId()).isEqualTo(5L);
        assertThat(response.relatedGoalId()).isEqualTo(2L);
        assertThat(response.relatedDreamId()).isEqualTo(1L);
        assertThat(response.placement()).isEqualTo(ObstaclePlacement.TASK);
    }

    @Test
    void aTaskFromAnotherStepIsRefused() {
        assertThatThrownBy(() -> service.createObstacle(linked(ObstacleType.MONEY, null, null, 6L, 7L, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("That task is not part of the chosen step.");
    }

    @Test
    void blc03_aPartnerConflictMayStayUnplaced() {
        ObstacleResponse response = service.createObstacle(
                linked(ObstacleType.PARTNER, 1L, null, null, null, "We argued about authorship."));

        assertThat(response.placement()).isEqualTo(ObstaclePlacement.NONE);
        assertThat(response.placementExempt()).isTrue();
        assertThat(response.relatedDreamId()).isEqualTo(1L);
    }

    @Test
    void aPartnerObstacleWithoutAnyWorksheetAnswerIsNotExempt() {
        assertThatThrownBy(() -> service.createObstacle(linked(ObstacleType.PARTNER, 1L, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Put this blocker on a step or a task.");
    }

    @Test
    void theUnplacedPathKeepsAGoalAndTakesTheDreamFromIt() {
        ObstacleResponse response = service.createObstacleAllowingUnplaced(
                linked(ObstacleType.MONEY, 9L, 2L, null, null, null));

        assertThat(response.placement()).isEqualTo(ObstaclePlacement.NONE);
        assertThat(response.relatedGoalId()).isEqualTo(2L);
        assertThat(response.relatedDreamId()).isEqualTo(1L);
    }

    @Test
    void blc04_anOlderUnplacedObstacleCanStillBeEditedAndResolved() {
        Obstacle stored = Obstacle.builder().id(20L).user(testUser).title("Funding").relatedDream(dream)
                .obstacleType(ObstacleType.MONEY).severity(Severity.HIGH).status(ObstacleStatus.OPEN).archived(false).build();
        when(obstacleRepository.findById(20L)).thenReturn(Optional.of(stored));
        ObstacleRequest resolve = new ObstacleRequest(1L, null, null, null, "Funding", null,
                ObstacleType.MONEY, Severity.HIGH, null, null, "No budget line", null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, ObstacleStatus.RESOLVED);

        ObstacleResponse response = service.updateObstacle(20L, resolve);

        assertThat(response.status()).isEqualTo(ObstacleStatus.RESOLVED);
        assertThat(response.relatedDreamId()).isEqualTo(1L);
        assertThat(response.placement()).isEqualTo(ObstaclePlacement.NONE);
    }

    @Test
    void movingAnUnplacedObstacleToAnotherDreamNeedsAStep() {
        Obstacle stored = Obstacle.builder().id(21L).user(testUser).title("Funding").relatedDream(dream)
                .obstacleType(ObstacleType.MONEY).severity(Severity.HIGH).status(ObstacleStatus.OPEN).archived(false).build();
        when(obstacleRepository.findById(21L)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.updateObstacle(21L, linked(ObstacleType.MONEY, 9L, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Put this blocker on a step or a task.");
    }

    @Test
    void blc05_movingAnUnplacedObstacleToAStepDerivesItsGoalAndDream() {
        Obstacle stored = Obstacle.builder().id(22L).user(testUser).title("Funding").relatedDream(otherDream)
                .obstacleType(ObstacleType.MONEY).severity(Severity.HIGH).status(ObstacleStatus.OPEN).archived(false).build();
        when(obstacleRepository.findById(22L)).thenReturn(Optional.of(stored));

        ObstacleResponse response = service.updateObstacle(22L, linked(ObstacleType.MONEY, 9L, null, 5L, null, null));

        assertThat(response.relatedStepId()).isEqualTo(5L);
        assertThat(response.relatedGoalId()).isEqualTo(2L);
        assertThat(response.relatedDreamId()).isEqualTo(1L);
    }

    @Test
    void takingAPlacedObstacleOffItsStepIsRefused() {
        Obstacle stored = Obstacle.builder().id(23L).user(testUser).title("Funding")
                .obstacleType(ObstacleType.MONEY).severity(Severity.HIGH).status(ObstacleStatus.OPEN).archived(false).build();
        stored.anchorTo(step, null);
        when(obstacleRepository.findById(23L)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.updateObstacle(23L, linked(ObstacleType.MONEY, 1L, 2L, null, null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void theUnplacedListHoldsUnplacedAndArchivedAnchorsButNotExemptOnes() {
        Obstacle unplaced = Obstacle.builder().id(30L).user(testUser).title("a").relatedDream(dream)
                .obstacleType(ObstacleType.MONEY).severity(Severity.LOW).status(ObstacleStatus.OPEN).build();
        Obstacle placed = Obstacle.builder().id(31L).user(testUser).title("b")
                .obstacleType(ObstacleType.MONEY).severity(Severity.LOW).status(ObstacleStatus.OPEN).build();
        placed.anchorTo(step, null);
        VisionStep archivedStep = VisionStep.builder().id(8L).user(testUser).goal(goal).archived(true).build();
        Obstacle onArchived = Obstacle.builder().id(32L).user(testUser).title("c")
                .obstacleType(ObstacleType.MONEY).severity(Severity.LOW).status(ObstacleStatus.OPEN).build();
        onArchived.anchorTo(archivedStep, null);
        Obstacle exempt = Obstacle.builder().id(33L).user(testUser).title("d").conflictIncident("Argued")
                .obstacleType(ObstacleType.PARTNER).severity(Severity.LOW).status(ObstacleStatus.OPEN).build();
        when(obstacleRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(java.util.List.of(unplaced, placed, onArchived, exempt));

        assertThat(service.listUnplacedObstacles()).extracting(ObstacleResponse::id).containsExactly(30L, 32L);
    }

    @Test
    void blc06_syncAfterAMoveFollowsTheAnchor() {
        Obstacle stored = Obstacle.builder().id(40L).user(testUser).title("Funding")
                .obstacleType(ObstacleType.MONEY).severity(Severity.HIGH).status(ObstacleStatus.OPEN).build();
        stored.anchorTo(null, task);
        Goal newGoal = Goal.builder().id(3L).user(testUser).dream(otherDream).build();
        step.setGoal(newGoal);

        stored.syncParentsFromAnchor();

        assertThat(stored.getRelatedGoal()).isSameAs(newGoal);
        assertThat(stored.getRelatedDream()).isSameAs(otherDream);
    }
}
