package com.visionmapping.service;

import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityAudit;
import com.visionmapping.dto.request.PlanShareRequest;
import com.visionmapping.dto.response.PlanShareResponse;
import com.visionmapping.dto.response.SharedPlanResponse;
import com.visionmapping.dto.response.SharedPlanResponse.SharedGoal;
import com.visionmapping.dto.response.SharedPlanResponse.SharedStep;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.PlanShare;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.PlanShareRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V9 (v0.1 FR-10): read-only links to one dream's plan for a partner or mentor.
 * Sharing is opt-in per dream, a link can be stopped at any time, and it ends
 * on its own after {@code expiresInDays}. A stopped, expired or unknown link,
 * or one whose dream is archived, all answer the same 404, so a link reveals
 * nothing once it no longer works.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PlanShareService {

    static final int DEFAULT_EXPIRY_DAYS = 90;
    /** 256 bits from SecureRandom: not guessable, so the hash need not be slow. */
    private static final int TOKEN_BYTES = 32;
    private static final String LINK_GONE = "This link has ended or was stopped.";

    private final EntityLookup lookup;
    private final SecurityEventService securityEvents;
    private final PlanShareRepository planShareRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<PlanShareResponse> list(Long dreamId) {
        Long userId = lookup.userId();
        List<PlanShare> shares = dreamId == null
                ? planShareRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                : planShareRepository.findByUser_IdAndDream_IdOrderByCreatedAtDesc(userId, lookup.dream(dreamId).getId());
        return shares.stream().map(share -> toResponse(share, null)).toList();
    }

    @SecurityAudit(value = SecurityEventType.SHARE_LINK_CREATED, target = "plan_share")
    public PlanShareResponse create(PlanShareRequest request) {
        Dream dream = lookup.dream(request.dreamId());
        if (dream.isArchived()) {
            throw new BusinessRuleException("Restore this dream before you share it.");
        }
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        int days = request.expiresInDays() == null ? DEFAULT_EXPIRY_DAYS : request.expiresInDays();
        PlanShare share = planShareRepository.save(PlanShare.builder()
                .user(lookup.currentUser())
                .dream(dream)
                .partner(lookup.optionalPartner(request.partnerId()))
                .tokenHash(hash(token))
                .message(blankToNull(request.message()))
                .expiresAt(Instant.now(clock).plus(Duration.ofDays(days)))
                .build());
        return toResponse(share, token);
    }

    @SecurityAudit(value = SecurityEventType.SHARE_LINK_REVOKED, target = "plan_share")
    public PlanShareResponse revoke(Long id) {
        PlanShare share = planShareRepository.findById(id)
                .filter(found -> found.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Share not found: " + id));
        if (share.getRevokedAt() == null) {
            share.setRevokedAt(Instant.now(clock));
        }
        return toResponse(share, null);
    }

    /** Public: the partner's view. Counts the visit so the owner sees the link was opened. */
    public SharedPlanResponse view(String token) {
        Instant now = Instant.now(clock);
        PlanShare share = planShareRepository.findByTokenHash(hash(token))
                .filter(found -> found.isOpenAt(now) && !found.getDream().isArchived())
                .orElse(null);
        // V10 (SEC-01-07): every opening is logged, and so is every refused one.
        if (share == null) {
            securityEvents.recordFor(null, SecurityEventType.SHARE_LINK_OPENED, SecurityOutcome.DENIED,
                    "unknown, expired or stopped link");
            throw new ResourceNotFoundException(LINK_GONE);
        }
        securityEvents.recordFor(null, SecurityEventType.SHARE_LINK_OPENED, SecurityOutcome.SUCCESS,
                "plan_share", share.getId(), null);
        share.setViewCount(share.getViewCount() + 1);
        share.setLastViewedAt(now);

        Dream dream = share.getDream();
        Long userId = share.getUser().getId();
        List<Goal> goals = goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(dream.getId(), userId);
        List<SharedGoal> sharedGoals = goals.stream()
                .sorted(Comparator.comparing(Goal::getTargetDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Goal::getId))
                .map(goal -> new SharedGoal(goal.getTitle(), goal.getStatus(), goal.getTargetDate(), goal.getProgressPercent(),
                        visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), userId).stream()
                                .sorted(Comparator.comparing(VisionStep::getSequenceNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                                        .thenComparing(VisionStep::getId))
                                .map(step -> new SharedStep(step.getTitle(), step.getStatus(), step.getTargetDate()))
                                .toList()))
                .toList();
        return new SharedPlanResponse(firstName(share.getUser().getFullName()),
                share.getPartner() == null ? null : share.getPartner().getName(),
                share.getMessage(), dream.getTitle(), dream.getDescription(), dream.getSuccessDefinition(),
                dream.getTargetDate(), averageProgress(goals), share.getExpiresAt(), sharedGoals);
    }

    private PlanShareResponse toResponse(PlanShare share, String token) {
        return new PlanShareResponse(share.getId(), share.getDream().getId(), share.getDream().getTitle(),
                share.getPartner() == null ? null : share.getPartner().getId(),
                share.getPartner() == null ? null : share.getPartner().getName(),
                share.getMessage(), share.getCreatedAt(), share.getExpiresAt(), share.getRevokedAt(),
                share.isOpenAt(Instant.now(clock)), share.getLastViewedAt(), share.getViewCount(), token);
    }

    private static BigDecimal averageProgress(List<Goal> goals) {
        if (goals.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return goals.stream()
                .map(goal -> goal.getProgressPercent() == null ? BigDecimal.ZERO : goal.getProgressPercent())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(goals.size()), 0, RoundingMode.HALF_UP);
    }

    private static String firstName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
