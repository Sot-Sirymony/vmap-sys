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
import org.springframework.lang.Nullable;
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

    private static final int RETAKE_AFTER_DAYS = 84;
    private static final int RETAKE_AFTER_MODULE_DAYS = 28;

    private final EntityLookup lookup;
    private final ChainAssessmentRepository chainAssessmentRepository;
    private final com.visionmapping.repository.ModuleCompletionRepository completionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ChainAssessmentSummaryResponse summary() {
        return summary(lookup.userId());
    }

    /** The same summary for any user; callers check they may see that user (V9 coach view). */
    @Transactional(readOnly = true)
    public ChainAssessmentSummaryResponse summary(Long userId) {
        List<ChainAssessment> takes = chainAssessmentRepository.findByUser_IdOrderByTakenOnAsc(userId);
        List<ChainAssessmentResponse> assessments = takes.stream()
                .map(ChainAssessmentService::toResponse)
                .toList();
        BigDecimal improvement = null;
        boolean retakeSuggested = false;
        boolean retakeAfterModule = false;
        if (!assessments.isEmpty()) {
            ChainAssessmentResponse latest = assessments.get(assessments.size() - 1);
            retakeSuggested = !latest.takenOn().plusDays(RETAKE_AFTER_DAYS).isAfter(LocalDate.now(clock));
            // V15 (AH-FR-28): a rule, no AI.
            LocalDate takenOn = latest.takenOn();
            retakeAfterModule = !takenOn.plusDays(RETAKE_AFTER_MODULE_DAYS).isAfter(LocalDate.now(clock))
                    && completionRepository.findByUser_Id(userId).stream()
                    .anyMatch(completion -> completion.getCompletedAt() != null
                            && completion.getCompletedAt().atZone(clock.getZone()).toLocalDate().isAfter(takenOn));
            if (takes.size() > 1) {
                improvement = sharedImprovement(takes.get(0), takes.get(takes.size() - 1));
            }
        }
        return new ChainAssessmentSummaryResponse(assessments, improvement, retakeSuggested, retakeAfterModule);
    }

    public ChainAssessmentResponse save(ChainAssessmentRequest request) {
        requireNotFutureDay(request.takenOn(), clock);
        ChainAssessment entity = chainAssessmentRepository.findByUser_IdAndTakenOn(lookup.userId(), request.takenOn())
                .orElseGet(() -> ChainAssessment.builder().user(lookup.currentUser()).takenOn(request.takenOn()).build());
        entity.setSettlingScore(request.settlingScore());
        entity.setFearScore(request.fearScore());
        entity.setCriticismScore(request.criticismScore());
        entity.setPrioritiesScore(request.prioritiesScore());
        entity.setPersistenceScore(request.persistenceScore());
        entity.setVisionScore(request.visionScore());
        // V17 BL-FR-31: a new take measures the current six only; retaking an older same-day take drops the retired two.
        entity.setKnowledgeScore(null);
        entity.setResourcesScore(null);
        return toResponse(chainAssessmentRepository.save(entity));
    }

    /**
     * V17 BL-FR-31: the change from baseline to latest over the chains both
     * measured, so a retired or new chain never skews it. Null when they share none.
     */
    @Nullable
    static BigDecimal sharedImprovement(ChainAssessment baseline, ChainAssessment latest) {
        List<ModuleJourneyService.Chain> shared = ModuleJourneyService.ALL_CHAINS.stream()
                .filter(chain -> chain.measuredIn(baseline) && chain.measuredIn(latest))
                .toList();
        if (shared.isEmpty()) {
            return null;
        }
        return improvementPercent(average(shared, baseline), average(shared, latest));
    }

    private static BigDecimal average(List<ModuleJourneyService.Chain> chains, ChainAssessment take) {
        int total = chains.stream().mapToInt(chain -> chain.scoreOf(take)).sum();
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(chains.size()), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal improvementPercent(BigDecimal baseline, BigDecimal latest) {
        return latest.subtract(baseline).multiply(BigDecimal.valueOf(100)).divide(baseline, 1, RoundingMode.HALF_UP);
    }

    /** The average covers the chains this take measured (six on every take, old or new). */
    private static ChainAssessmentResponse toResponse(ChainAssessment entity) {
        List<ModuleJourneyService.Chain> measured = ModuleJourneyService.ALL_CHAINS.stream()
                .filter(chain -> chain.measuredIn(entity))
                .toList();
        BigDecimal average = measured.isEmpty() ? BigDecimal.ZERO.setScale(2) : average(measured, entity);
        return new ChainAssessmentResponse(entity.getId(), entity.getTakenOn(), entity.getSettlingScore(), entity.getFearScore(),
                entity.getCriticismScore(), entity.getVisionScore(), entity.getKnowledgeScore(), entity.getResourcesScore(), average,
                entity.getPrioritiesScore(), entity.getPersistenceScore());
    }
}
