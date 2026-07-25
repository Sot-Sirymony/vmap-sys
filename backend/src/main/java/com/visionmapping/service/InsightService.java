package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.isBlank;

import com.visionmapping.dto.response.InsightResponse;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.Review;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-36 (PKM Insight Library): a read-only view over lessons the user has
 * already captured — Review.lessonsLearned (FR-9) and Obstacle.rootCause /
 * creativeAlternatives (FR-32) — searchable by free text. It authors nothing;
 * every item carries its source record's id so the UI can link back to it for
 * editing (FR-36.3), and everything is scoped strictly to the authenticated
 * user's own reviews and obstacles (BR-30).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsightService {

    private final EntityLookup lookup;
    private final ReviewRepository reviewRepository;
    private final ObstacleRepository obstacleRepository;
    private final Clock clock;

    public List<InsightResponse> searchInsights(String query) {
        Long userId = lookup.userId();
        List<InsightResponse> insights = new ArrayList<>();

        for (Review review : reviewRepository.findByUser_IdAndArchivedFalse(userId)) {
            if (!isBlank(review.getLessonsLearned())) {
                insights.add(new InsightResponse("REVIEW", "LESSON_LEARNED", review.getId(),
                        review.getReviewType().name(), review.getLessonsLearned(),
                        review.getReviewDate() == null ? null : review.getReviewDate().toLocalDate()));
            }
        }
        for (Obstacle obstacle : obstacleRepository.findByUser_IdAndArchivedFalse(userId)) {
            LocalDate date = obstacle.getUpdatedAt() == null
                    ? null
                    : obstacle.getUpdatedAt().atZone(clock.getZone()).toLocalDate();
            if (!isBlank(obstacle.getRootCause())) {
                insights.add(new InsightResponse("OBSTACLE", "ROOT_CAUSE", obstacle.getId(),
                        obstacle.getTitle(), obstacle.getRootCause(), date));
            }
            if (!isBlank(obstacle.getCreativeAlternatives())) {
                insights.add(new InsightResponse("OBSTACLE", "CREATIVE_ALTERNATIVES", obstacle.getId(),
                        obstacle.getTitle(), obstacle.getCreativeAlternatives(), date));
            }
        }

        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return insights.stream()
                .filter(insight -> needle.isEmpty() || matches(insight, needle))
                .sorted(Comparator.comparing(InsightResponse::date, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static boolean matches(InsightResponse insight, String needle) {
        return insight.content().toLowerCase(Locale.ROOT).contains(needle)
                || (insight.sourceTitle() != null && insight.sourceTitle().toLowerCase(Locale.ROOT).contains(needle));
    }
}
