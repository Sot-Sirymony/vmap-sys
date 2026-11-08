package com.visionmapping.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * BRD 13 EJ-FR-26: one shared rule finds the vision area of a linked record,
 * checking area, task, step, goal and dream in that order — the same order the
 * four per-entity copies in DashboardService used.
 */
class PlanLinkedTest {

    private final VisionArea career = VisionArea.builder().id(1L).build();
    private final VisionArea health = VisionArea.builder().id(2L).build();
    private final Dream dream = Dream.builder().id(10L).visionArea(career).build();
    private final Goal goal = Goal.builder().id(20L).dream(dream).build();
    private final VisionStep step = VisionStep.builder().id(30L).goal(goal).build();
    private final TaskItem task = TaskItem.builder().id(40L).step(step).build();

    @Test
    void theCoreHierarchyWalksUpToItsArea() {
        assertThat(dream.visionAreaId()).isEqualTo(1L);
        assertThat(goal.visionAreaId()).isEqualTo(1L);
        assertThat(step.visionAreaId()).isEqualTo(1L);
        assertThat(task.visionAreaId()).isEqualTo(1L);
        assertThat(TaskItem.builder().build().visionAreaId()).isNull();
    }

    @Test
    void aDirectAreaLinkWinsOverDeeperLinks() {
        Partner partner = Partner.builder().relatedVisionArea(health).relatedTask(task).build();

        assertThat(partner.linkedVisionAreaId()).isEqualTo(2L);
    }

    @Test
    void eachKindOfRecordFindsItsAreaThroughWhatItLinksTo() {
        assertThat(Obstacle.builder().relatedStep(step).build().linkedVisionAreaId()).isEqualTo(1L);
        assertThat(Obstacle.builder().relatedDream(dream).build().linkedVisionAreaId()).isEqualTo(1L);
        assertThat(ProgressLog.builder().relatedTask(task).build().linkedVisionAreaId()).isEqualTo(1L);
        assertThat(Review.builder().relatedDream(dream).build().linkedVisionAreaId()).isEqualTo(1L);
        assertThat(CommunicationMessage.builder().relatedGoal(goal).build().linkedVisionAreaId()).isEqualTo(1L);
    }

    @Test
    void aRecordThatLinksToNothingHasNoArea() {
        assertThat(Obstacle.builder().build().linkedVisionAreaId()).isNull();
        assertThat(Partner.builder().build().linkedVisionAreaId()).isNull();
    }
}
