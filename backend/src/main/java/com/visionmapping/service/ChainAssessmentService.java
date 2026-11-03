package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.requireNotFutureDay;

import com.visionmapping.dto.request.ChainAssessmentRequest;
import com.visionmapping.dto.response.ChainAssessmentResponse;
import com.visionmapping.dto.response.ChainAssessmentSummaryResponse;
import com.visionmapping.entity.ChainAssessment;
import com.visionmapping.repository.ChainAssessmentRepository;
import com.visionmapping.service.support.EntityLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-71: the six-chain self-assessment. The first one is the baseline; each
 * later one is compared against it. Taking it twice in a day replaces that day's.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ChainAssessmentService {

    private static final int CHAIN_COUNT = 6;
    private static final int RETAKE_AFTER_DAYS = 84;

    private final EntityLookup lookup;
    private final ChainAssessmentRepository chainAssessmentRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ChainAssessmentSummaryResponse summary() {
        return summary(lookup.userId());
    }

    /** The same summary for any user; callers check they may see that user (V9 coach view). */
    @Transactional(readOnly = true)
    public ChainAssessmentSummaryResponse summary(Long userId) {
        List<ChainAssessmentResponse> assessments = chainAssessmentRepository.findByUser_IdOrderByTakenOnAsc(userId).stream()
                .map(ChainAssessmentService::toResponse)
                .toList();
        BigDecimal improvement = null;
        boolean retakeSuggested = false;
        if (!assessments.isEmpty()) {
            ChainAssessmentResponse latest = assessments.get(assessments.size() - 1);
            retakeSuggested = !latest.takenOn().plusDays(RETAKE_AFTER_DAYS).isAfter(LocalDate.now(clock));
            if (assessments.size() > 1) {
                improvement = improvementPercent(assessments.get(0).averageScore(), latest.averageScore());
            }
        }
        return new ChainAssessmentSummaryResponse(assessments, improvement, retakeSuggested);
    }

    public ChainAssessmentResponse save(ChainAssessmentRequest request) {
        requireNotFutureDay(request.takenOn(), clock);
        ChainAssessment entity = chainAssessmentRepository.findByUser_IdAndTakenOn(lookup.userId(), request.takenOn())
                .orElseGet(() -> ChainAssessment.builder().user(lookup.currentUser()).takenOn(request.takenOn()).build());
        entity.setSettlingScore(request.settlingScore());
        entity.setFearScore(request.fearScore());
        entity.setCriticismScore(request.criticismScore());
        entity.setVisionScore(request.visionScore());
        entity.setKnowledgeScore(request.knowledgeScore());
        entity.setResourcesScore(request.resourcesScore());
        return toResponse(chainAssessmentRepository.save(entity));
    }

    static BigDecimal improvementPercent(BigDecimal baseline, BigDecimal latest) {
        return latest.subtract(baseline).multiply(BigDecimal.valueOf(100)).divide(baseline, 1, RoundingMode.HALF_UP);
    }

    private static ChainAssessmentResponse toResponse(ChainAssessment entity) {
        int total = entity.getSettlingScore() + entity.getFearScore() + entity.getCriticismScore()
                + entity.getVisionScore() + entity.getKnowledgeScore() + entity.getResourcesScore();
        BigDecimal average = BigDecimal.valueOf(total).divide(BigDecimal.valueOf(CHAIN_COUNT), 2, RoundingMode.HALF_UP);
        return new ChainAssessmentResponse(entity.getId(), entity.getTakenOn(), entity.getSettlingScore(), entity.getFearScore(),
                entity.getCriticismScore(), entity.getVisionScore(), entity.getKnowledgeScore(), entity.getResourcesScore(), average);
    }
}
