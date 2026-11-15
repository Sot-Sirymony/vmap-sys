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
import java.util.function.Function;
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

    /**
     * One barrier of the six-chain assessment, the statement it is scored on,
     * and the module that works on it. A take may not have measured it (the
     * chains changed in V17), so its score can be null.
     */
    public record Chain(String key, String label, String statement, int module, Function<ChainAssessment, Integer> score) {

        @Nullable
        public Integer scoreOf(ChainAssessment take) {
            return score.apply(take);
        }

        public boolean measuredIn(ChainAssessment take) {
            return scoreOf(take) != null;
        }
    }

    /**
     * V17 BL-FR-30 (D-17-3): the six chains, in the method's order, each with
     * the module that works on it. Original wording (BL-NFR-05).
     */
    public static final List<Chain> CHAINS = List.of(
            new Chain("settlingScore", "Settling for average", "I expect more of myself than what is comfortable.", 1,
                    ChainAssessment::getSettlingScore),
            new Chain("fearScore", "Fear of failure", "I try things even when they might not work.", 2,
                    ChainAssessment::getFearScore),
            new Chain("criticismScore", "Avoiding criticism", "What others may say does not stop me.", 3,
                    ChainAssessment::getCriticismScore),
            new Chain("prioritiesScore", "Misplaced priorities", "My time goes to what matters most, not to what is loudest.", 13,
                    ChainAssessment::getPrioritiesScore),
            new Chain("persistenceScore", "Giving up too soon", "When progress is slow, I keep going instead of quitting.", 12,
                    ChainAssessment::getPersistenceScore),
            new Chain("visionScore", "Unclear vision", "I can describe exactly what I want and when.", 4,
                    ChainAssessment::getVisionScore));

    /**
     * V17 BL-FR-31, BL-FR-32: chains measured before V17 and now handled as
     * step gaps. Shown on past takes as "No longer measured", never asked.
     */
    public static final List<Chain> RETIRED_CHAINS = List.of(
            new Chain("knowledgeScore", "Not knowing how", "When I do not know how, I find out instead of stopping.", 5,
                    ChainAssessment::getKnowledgeScore),
            new Chain("resourcesScore", "Lack of resources", "When I lack something, I look for who could supply it.", 6,
                    ChainAssessment::getResourcesScore));

    /** Every chain a take may hold: the current six, then the retired ones. */
    public static final List<Chain> ALL_CHAINS = java.util.stream.Stream.concat(CHAINS.stream(), RETIRED_CHAINS.stream()).toList();
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
            ChainAssessment take = latest.get();
            Optional<Chain> low = CHAINS.stream()
                    .filter(chain -> chain.measuredIn(take) && chain.scoreOf(take) <= LOW_SCORE)
                    .filter(chain -> available.stream().anyMatch(module -> module.number() == chain.module()))
                    .min(Comparator.comparingInt((Chain chain) -> chain.scoreOf(take))
                            .thenComparingInt(chain -> position(available, chain.module())));
            if (low.isPresent()) {
                LearningModuleSummaryResponse module = byNumber(available, low.get().module());
                return new ModuleNext(module.number(), module.title(), "Your latest assessment scored " + low.get().label() + " "
                        + low.get().scoreOf(take) + " of 5, and this module works on it.", "LOW_CHAIN", "RULE", null);
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
        // V17 BL-FR-31: each chain from the first take that measured it to the latest that did.
        List<ChainChange> chains = new ArrayList<>();
        for (Chain chain : CHAINS) {
            List<ChainAssessment> measured = takes.stream().filter(chain::measuredIn).toList();
            if (!measured.isEmpty()) {
                chains.add(new ChainChange(chain.key(), chain.label(), chain.scoreOf(measured.get(0)),
                        chain.scoreOf(measured.get(measured.size() - 1))));
            }
        }
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
