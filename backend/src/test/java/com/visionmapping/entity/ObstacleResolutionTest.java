package com.visionmapping.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.entity.enums.ObstacleStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** V19 RM-FR-08: an obstacle remembers when it was first closed, and forgets it when reopened. */
class ObstacleResolutionTest {

    private static final Instant MONDAY = Instant.parse("2026-11-16T09:00:00Z");
    private static final Instant TUESDAY = Instant.parse("2026-11-17T09:00:00Z");

    @Test
    void closingStampsTheFirstDateAndReopeningClearsIt() {
        Obstacle obstacle = Obstacle.builder().status(ObstacleStatus.OPEN).build();
        obstacle.recordResolution(MONDAY);
        assertThat(obstacle.getResolvedAt()).isNull();

        obstacle.setStatus(ObstacleStatus.RESOLVED);
        obstacle.recordResolution(MONDAY);
        assertThat(obstacle.getResolvedAt()).isEqualTo(MONDAY);

        // Moving between the two closed statuses keeps the first date.
        obstacle.setStatus(ObstacleStatus.ACCEPTED);
        obstacle.recordResolution(TUESDAY);
        assertThat(obstacle.getResolvedAt()).isEqualTo(MONDAY);

        obstacle.setStatus(ObstacleStatus.IN_PROGRESS);
        obstacle.recordResolution(TUESDAY);
        assertThat(obstacle.getResolvedAt()).isNull();
    }
}
