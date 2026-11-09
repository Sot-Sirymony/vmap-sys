package com.visionmapping.service;

import com.visionmapping.dto.response.CommunicationHelpResponses.FollowUpDue;
import com.visionmapping.dto.response.CommunicationHelpResponses.RankedMessage;
import com.visionmapping.entity.CommunicationMessage;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.enums.CommunicationStatus;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V15 Phase 3: the Communication lists that need no AI (AH-NFR-03). Messages
 * due a follow-up (AH-FR-48) and the order to send drafts in (AH-FR-66). The
 * participant's own messages, shown only to them.
 */
@Service
@RequiredArgsConstructor
public class CommunicationSupportService {

    private static final Set<CommunicationStatus> AWAITING_REPLY = Set.of(CommunicationStatus.SENT, CommunicationStatus.FOLLOWED_UP);

    private final EntityLookup lookup;
    private final CommunicationMessageRepository messageRepository;
    private final Clock clock;

    /** AH-FR-48: Sent or Followed Up messages whose follow-up date has passed, the longest overdue first. */
    @Transactional(readOnly = true)
    public List<FollowUpDue> followUpDue() {
        LocalDate today = LocalDate.now(clock);
        return messageRepository.findByUser_Id(lookup.userId()).stream()
                .filter(message -> !message.isArchived() && AWAITING_REPLY.contains(message.getStatus()))
                .filter(message -> message.getFollowUpDate() != null && message.getFollowUpDate().isBefore(today))
                .sorted(Comparator.comparing(CommunicationMessage::getFollowUpDate).thenComparing(CommunicationMessage::getId))
                .map(message -> new FollowUpDue(message.getId(), message.getSubject(), message.getRequest(), message.getStatus().name(),
                        message.getFollowUpDate(), message.getPartner() == null ? null : message.getPartner().getId(),
                        message.getPartner() == null ? null : message.getPartner().getName(),
                        ChronoUnit.DAYS.between(message.getFollowUpDate(), today)))
                .toList();
    }

    /**
     * AH-FR-66: Draft messages in the rule order: those whose linked task is
     * Blocked first, then by the linked task's due date (none last), then the
     * longest-waiting draft first.
     */
    @Transactional(readOnly = true)
    public List<RankedMessage> draftPriority() {
        Instant now = Instant.now(clock);
        return messageRepository.findByUser_Id(lookup.userId()).stream()
                .filter(message -> !message.isArchived() && message.getStatus() == CommunicationStatus.DRAFT)
                .map(message -> {
                    TaskItem task = message.getRelatedTask();
                    return new RankedMessage(message.getId(), message.getSubject(),
                            message.getPartner() == null ? null : message.getPartner().getName(),
                            task == null ? null : task.getDueDate(), task != null && task.getStatus() == WorkStatus.BLOCKED,
                            message.getCreatedAt() == null ? 0 : Math.max(0, Duration.between(message.getCreatedAt(), now).toDays()), null);
                })
                .sorted(Comparator.comparing(RankedMessage::taskBlocked).reversed()
                        .thenComparing(RankedMessage::taskDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Comparator.comparingLong(RankedMessage::daysWaiting).reversed())
                        .thenComparing(RankedMessage::id))
                .toList();
    }
}
