package com.visionmapping.service;

import com.visionmapping.dto.response.LearningModuleSummaryResponse;
import com.visionmapping.dto.response.ModuleJourneyResponses.ChainChange;
import com.visionmapping.dto.response.ModuleJourneyResponses.Journey;
import com.visionmapping.dto.response.ModuleJourneyResponses.ModuleNext;
import com.visionmapping.dto.response.ModuleJourneyResponses.StageProgress;
import com.visionmapping.entity.ChainAssessment;
import com.visionmapping.entity.enums.ModuleStatus;
import com.visionmapping.repository.ChainAssessmentRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V15 Phase 4: the Modules helpers that need no AI (AH-NFR-03). The rule for
 * "Which module next?" (AH-FR-14) and the journey summary (AH-FR-15).
 */
@Service
@RequiredArgsConstructor
public class ModuleJourneyService {

    /** One barrier of the six-chain assessment and the Break the Chains module that works on it. */
    public record Chain(String key, String label, int module, ToIntFunction<ChainAssessment> score) {
    }

    /** The six barriers, each with the module that works on it. */
    public static final List<Chain> CHAINS = List.of(
            new Chain("settlingScore", "Settling for average", 1, ChainAssessment::getSettlingScore),
            new Chain("fearScore", "Fear of failure", 2, ChainAssessment::getFearScore),
            new Chain("criticismScore", "Avoiding criticism", 3, ChainAssessment::getCriticismScore),
            new Chain("visionScore", "Unclear vision", 4, ChainAssessment::getVisionScore),
            new Chain("knowledgeScore", "Not knowing how", 5, ChainAssessment::getKnowledgeScore),
            new Chain("resourcesScore", "Lack of resources", 6, ChainAssessment::getResourcesScore));
    /** A chain scored this or lower is holding the participant back (AH-FR-14). */
    static final int LOW_SCORE = 2;

    private final EntityLookup lookup;
    private final LearningModuleService moduleService;
    private final ChainAssessmentRepository assessmentRepository;

    /** The modules a recommendation may name: unlocked and not completed, in program order. */
    @Transactional(readOnly = true)
    public List<LearningModuleSummaryResponse> available(String language) {
        return moduleService.listModules(language).stream().filter(module -> module.status() == ModuleStatus.AVAILABLE).toList();
    }

    /**
     * AH-FR-14: the next module in program order, unless the latest assessment
     * has a chain scored 1 or 2 whose module is unlocked and not completed;
     * then that module (the lowest score first). The reason names the rule.
     */
    @Transactional(readOnly = true)
    public ModuleNext next(String language) {
        List<LearningModuleSummaryResponse> available = available(language);
        if (available.isEmpty()) {
            return new ModuleNext(null, null, "You have finished every module you can open.", "ALL_DONE", "RULE", null);
        }
        Optional<ChainAssessment> latest = latest();
        if (latest.isPresent()) {
            Optional<Chain> low = CHAINS.stream()
                    .filter(chain -> chain.score().applyAsInt(latest.get()) <= LOW_SCORE)
                    .filter(chain -> available.stream().anyMatch(module -> module.number() == chain.module()))
                    .min(Comparator.comparingInt((Chain chain) -> chain.score().applyAsInt(latest.get()))
                            .thenComparingInt(chain -> position(available, chain.module())));
            if (low.isPresent()) {
                LearningModuleSummaryResponse module = byNumber(available, low.get().module());
                return new ModuleNext(module.number(), module.title(), "Your latest assessment scored " + low.get().label() + " "
                        + low.get().score().applyAsInt(latest.get()) + " of 5, and this module works on it.", "LOW_CHAIN", "RULE", null);
            }
        }
        LearningModuleSummaryResponse first = available.get(0);
        return new ModuleNext(first.number(), first.title(), "This is the next module in the program's order.", "PROGRAM_ORDER", "RULE", null);
    }

    /** AH-FR-15: modules done per stage, chain scores since the first assessment, and the next stage to reach. */
    @Transactional(readOnly = true)
    public Journey journey(String language) {
        List<LearningModuleSummaryResponse> modules = moduleService.listModules(language);
        Map<String, int[]> stages = new LinkedHashMap<>();
        for (LearningModuleSummaryResponse module : modules) {
            int[] counts = stages.computeIfAbsent(module.stage().name(), key -> new int[2]);
            counts[1]++;
            if (module.status() == ModuleStatus.COMPLETED) {
                counts[0]++;
            }
        }
        List<StageProgress> progress = new ArrayList<>();
        String nextStage = null;
        for (Map.Entry<String, int[]> entry : stages.entrySet()) {
            progress.add(new StageProgress(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
            if (nextStage == null && entry.getValue()[0] < entry.getValue()[1]) {
                nextStage = entry.getKey();
            }
        }
        List<ChainAssessment> takes = assessmentRepository.findByUser_IdOrderByTakenOnAsc(lookup.userId());
        List<ChainChange> chains = takes.isEmpty() ? List.of() : CHAINS.stream()
                .map(chain -> new ChainChange(chain.key(), chain.label(), chain.score().applyAsInt(takes.get(0)),
                        chain.score().applyAsInt(takes.get(takes.size() - 1))))
                .toList();
        int done = (int) modules.stream().filter(module -> module.status() == ModuleStatus.COMPLETED).count();
        return new Journey(done, modules.size(), progress, takes.size(), chains, nextStage);
    }

    private Optional<ChainAssessment> latest() {
        List<ChainAssessment> takes = assessmentRepository.findByUser_IdOrderByTakenOnAsc(lookup.userId());
        return takes.isEmpty() ? Optional.empty() : Optional.of(takes.get(takes.size() - 1));
    }

    private static int position(List<LearningModuleSummaryResponse> modules, int number) {
        LearningModuleSummaryResponse module = byNumber(modules, number);
        return module == null ? Integer.MAX_VALUE : module.position();
    }

    @Nullable
    private static LearningModuleSummaryResponse byNumber(List<LearningModuleSummaryResponse> modules, int number) {
        return modules.stream().filter(module -> module.number() == number).findFirst().orElse(null);
    }
}
