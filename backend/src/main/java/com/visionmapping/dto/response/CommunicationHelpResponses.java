package com.visionmapping.dto.response;

import com.visionmapping.dto.response.PageAssistantResponses.PageChange;
import com.visionmapping.util.Copies;
import java.time.LocalDate;
import java.util.List;

/** V15 Phase 3: what the Communication helpers answer. */
public final class CommunicationHelpResponses {

    private CommunicationHelpResponses() {
    }

    /** AH-FR-45: one problem with the message and, when there is one, a card that fixes it. */
    public record MessageIssue(String problem, PageChange fix) {
    }

    /**
     * AH-FR-49, AH-FR-65: a new message to the same partner, for the
     * participant to edit and save. Opens in the normal create form.
     */
    public record NewMessage(Long partnerId, String subject, String request, String messageBody, Long relatedDreamId,
                             Long relatedGoalId, Long relatedTaskId) {
    }

    /** AH-FR-64: the meeting plan as a task for the normal task form; titled with the partner's role, never the name. */
    public record MeetingTask(String title, String description, String owner) {
    }

    /** AH-FR-48: a Sent or Followed Up message past its follow-up date with no reply. */
    public record FollowUpDue(Long id, String subject, String request, String status, LocalDate followUpDate, Long partnerId,
                              String partnerName, long daysOverdue) {
    }

    /** AH-FR-66: one draft in the rule order, with Claude's reason when AI was used. */
    public record RankedMessage(Long id, String subject, String partnerName, LocalDate taskDueDate, boolean taskBlocked,
                                long daysWaiting, String reason) {
    }

    /** AH-FR-51: what a pasted reply says. kind is YES, NO, QUESTION or LATER. */
    public record ReplyReading(String kind, String nextAction) {
    }

    /** One helper answer. Only the parts of the mode are set. */
    public record CommunicationHelpAnswer(Long requestId, String mode, String note, List<PageChange> changes,
                                          List<MessageIssue> issues, NewMessage message, MeetingTask task,
                                          List<RankedMessage> ranking, List<String> sources, ReplyReading reading) {
        public CommunicationHelpAnswer {
            changes = Copies.list(changes);
            issues = Copies.list(issues);
            ranking = Copies.list(ranking);
            sources = Copies.list(sources);
        }

        /** Every mode but REPLY. */
        public CommunicationHelpAnswer(Long requestId, String mode, String note, List<PageChange> changes, List<MessageIssue> issues,
                                       NewMessage message, MeetingTask task, List<RankedMessage> ranking, List<String> sources) {
            this(requestId, mode, note, changes, issues, message, task, ranking, sources, null);
        }
    }
}
