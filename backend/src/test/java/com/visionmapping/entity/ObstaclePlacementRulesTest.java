package com.visionmapping.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.entity.enums.ExpectationAgreement;
import com.visionmapping.entity.enums.ObstaclePlacement;
import com.visionmapping.entity.enums.ObstacleType;
import org.junit.jupiter.api.Test;

/** V17 BL-FR-01, BL-FR-04, BL-FR-09: where an obstacle sits, and which partner conflicts may stay off the steps. */
class ObstaclePlacementRulesTest {

    @Test
    void onlyAPartnerConflictWithAnyWorksheetAnswerIsExempt() {
        assertThat(Obstacle.builder().obstacleType(ObstacleType.MONEY).conflictIncident("x").build().isPlacementExempt()).isFalse();
        assertThat(Obstacle.builder().obstacleType(ObstacleType.PARTNER).build().isPlacementExempt()).isFalse();
        assertThat(Obstacle.builder().obstacleType(ObstacleType.PARTNER).conflictIncident("  ").build().isPlacementExempt()).isFalse();
        assertThat(Obstacle.builder().obstacleType(ObstacleType.PARTNER).conflictIncident("We argued").build().isPlacementExempt()).isTrue();
        assertThat(Obstacle.builder().obstacleType(ObstacleType.PARTNER).criticismSubstance("Fair point").build().isPlacementExempt()).isTrue();
        assertThat(Obstacle.builder().obstacleType(ObstacleType.PARTNER).conflictNoCharacterAttacks(false).build().isPlacementExempt()).isTrue();
        assertThat(Obstacle.builder().obstacleType(ObstacleType.PARTNER).conflictDefinedWinWin(true).build().isPlacementExempt()).isTrue();
        assertThat(Obstacle.builder().obstacleType(ObstacleType.PARTNER).conflictExpectationAgreed(ExpectationAgreement.values()[0])
                .build().isPlacementExempt()).isTrue();
    }

    @Test
    void placementAndArchivedAnchorsFollowTheTaskOrStep() {
        VisionStep step = VisionStep.builder().build();
        TaskItem task = TaskItem.builder().step(step).build();
        Obstacle onTask = Obstacle.builder().relatedTask(task).relatedStep(step).build();
        Obstacle onStep = Obstacle.builder().relatedStep(step).build();
        Obstacle nowhere = Obstacle.builder().build();

        assertThat(onTask.placement()).isEqualTo(ObstaclePlacement.TASK);
        assertThat(onStep.placement()).isEqualTo(ObstaclePlacement.STEP);
        assertThat(nowhere.placement()).isEqualTo(ObstaclePlacement.NONE);
        assertThat(onStep.isAnchorArchived()).isFalse();
        assertThat(nowhere.isAnchorArchived()).isFalse();

        task.archive();
        assertThat(onTask.isAnchorArchived()).isTrue();
        step.archive();
        assertThat(onStep.isAnchorArchived()).isTrue();
    }

    @Test
    void leavingUnplacedKeepsTheGoalsOwnDream() {
        Dream dream = Dream.builder().build();
        Dream other = Dream.builder().build();
        Goal goal = Goal.builder().dream(dream).build();
        Obstacle obstacle = Obstacle.builder().build();

        obstacle.leaveUnplaced(other, goal);
        assertThat(obstacle.getRelatedDream()).isSameAs(dream);
        obstacle.leaveUnplaced(other, null);
        assertThat(obstacle.getRelatedDream()).isSameAs(other);
        assertThat(obstacle.getRelatedGoal()).isNull();
    }
}
