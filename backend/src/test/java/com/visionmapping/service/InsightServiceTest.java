package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.response.InsightResponse;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.Review;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.ReviewType;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * FR-36.1: the Insight Library aggregates lessons the user already captured
 * (Review.lessonsLearned, Obstacle.rootCause / creativeAlternatives), skips
 * blank fields, and filters by free-text query — all user-scoped (BR-30).
 */
@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock private EntityLookup lookup;
    @Mock private ReviewRepository reviewRepository;
    @Mock private ObstacleRepository obstacleRepository;

    private InsightService service;

    @BeforeEach
    void setUp() {
        service = new InsightService(lookup, reviewRepository, obstacleRepository, Clock.systemDefaultZone());
        when(lookup.userId()).thenReturn(1L);

        Review withLesson = Review.builder().id(1L).reviewType(ReviewType.WEEKLY)
                .reviewDate(LocalDateTime.of(2026, 7, 20, 9, 0)).lessonsLearned("Batch the literature search")
                .archived(false).build();
        Review blankLesson = Review.builder().id(2L).reviewType(ReviewType.DAILY)
                .reviewDate(LocalDateTime.of(2026, 7, 21, 9, 0)).lessonsLearned("   ").archived(false).build();
        Obstacle rootCauseOnly = Obstacle.builder().id(10L).title("Timeline slip").obstacleType(ObstacleType.TIME)
                .rootCause("Underestimated the scope").status(ObstacleStatus.RESOLVED).archived(false).build();
        Obstacle alternativesOnly = Obstacle.builder().id(11L).title("Stuck decision").obstacleType(ObstacleType.DECISION)
                .creativeAlternatives("Ask a mentor\nPrototype it\nDefer the call").status(ObstacleStatus.OPEN)
                .archived(false).build();

        when(reviewRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of(withLesson, blankLesson));
        when(obstacleRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of(rootCauseOnly, alternativesOnly));
    }

    @Test
    void aggregatesEveryNonBlankLessonSourceAndSkipsBlankOnes() {
        List<InsightResponse> insights = service.searchInsights("");

        // The blank lessons-learned review is skipped; the other three carry through.
        assertThat(insights).extracting(InsightResponse::kind)
                .containsExactlyInAnyOrder("LESSON_LEARNED", "ROOT_CAUSE", "CREATIVE_ALTERNATIVES");
    }

    @Test
    void filtersByFreeTextQueryCaseInsensitively() {
        List<InsightResponse> insights = service.searchInsights("MENTOR");

        assertThat(insights).extracting(InsightResponse::kind).containsExactly("CREATIVE_ALTERNATIVES");
    }
}
