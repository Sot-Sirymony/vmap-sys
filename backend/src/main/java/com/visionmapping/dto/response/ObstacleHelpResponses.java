package com.visionmapping.dto.response;

import com.visionmapping.dto.response.PageAssistantResponses.PageChange;
import com.visionmapping.util.Copies;
import java.time.LocalDate;
import java.util.List;

/** V15 Phase 2: what the obstacle helpers answer. */
public final class ObstacleHelpResponses {

    private ObstacleHelpResponses() {
    }

    /** AH-FR-35: one of the participant's own partners who fits the kind of help needed. Shown only to them. */
    public record PartnerMatch(Long id, String name, String role, String supportType, String status) {
    }

    /**
     * AH-FR-34, AH-FR-35: the kind of partner the obstacle type calls for, the
     * support type a new partner should get, and the participant's partners who fit.
     */
    public record PartnerMatches(String obstacleType, String suggestedSupportType, List<PartnerMatch> partners) {
        public PartnerMatches {
            partners = Copies.list(partners);
        }
    }

    /** AH-FR-39: a Blocked task with a blocker reason and no obstacle that names it. */
    public record BlockedTask(Long id, String code, String title, String blockerReason, Long stepId, String stepTitle,
                              Long goalId, Long dreamId) {
    }

    /** AH-FR-41: one open obstacle in the rule order, with the reason Claude gave when AI was used. */
    public record RankedObstacle(Long id, String title, String severity, int blockedTasks, long daysOpen, String reason) {
    }

    /** AH-FR-37: the proposed next task. dueDate is null when Claude's date broke a limit; latestDate is the step's date. */
    public record TaskProposal(String title, String description, String owner, LocalDate dueDate, String priority,
                               Long stepId, String stepTitle, LocalDate latestDate) {
    }

    /**
     * One helper answer. Only the parts of the mode are set: changes for
     * CLASSIFY, ROOT_CAUSE and SOLUTIONS; question while ROOT_CAUSE still asks;
     * task for NEXT_TASK; ranking for PRIORITISE.
     */
    public record ObstacleHelpAnswer(Long requestId, String mode, String note, List<PageChange> changes, String question,
                                     TaskProposal task, List<RankedObstacle> ranking, List<String> sources) {
        public ObstacleHelpAnswer {
            changes = Copies.list(changes);
            ranking = Copies.list(ranking);
            sources = Copies.list(sources);
        }
    }
}
