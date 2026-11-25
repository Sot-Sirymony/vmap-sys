package com.visionmapping.service;

import com.visionmapping.dto.response.StreamlineResponses;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.StreamlineNudge;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.StreamlineNudgeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * V32 (D-32-11, BR-32-10, SF-FR-14): the weekly Streamline nudge. While the
 * weekly review is due, at most one goal or step a week is suggested, the
 * highest-ranked rule candidate (rules only, never AI), and the same one at
 * most once every 30 days. The reminder email and the Dashboard show the same
 * nudge for the rest of the week.
 *
 * <p>Runs in its own transaction, because the Dashboard reads in a read-only one.
 */
@Service
@RequiredArgsConstructor
public class StreamlineNudges {

    static final Duration WEEK = Duration.ofDays(7);
    static final Duration SAME_ANCHOR = Duration.ofDays(30);

    private final StreamlineReviewService reviews;
    private final DailyPlanService dailyPlan;
    private final StreamlineNudgeRepository nudges;
    private final AppUserRepository users;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<StreamlineResponses.Candidate> nudgeFor(Long userId, LocalDate date) {
        AppUser user = users.findById(userId).orElse(null);
        if (user == null || !user.isStreamlineNudgesEnabled() || !dailyPlan.weeklyReviewDueFor(userId, date)) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        List<StreamlineNudge> recent = nudges.findByUser_IdAndSentAtAfterOrderBySentAtDesc(userId, now.minus(SAME_ANCHOR));
        List<StreamlineResponses.Candidate> candidates = reviews.candidatesFor(userId);
        Optional<StreamlineNudge> thisWeek = recent.stream().filter(nudge -> nudge.getSentAt().isAfter(now.minus(WEEK))).findFirst();
        if (thisWeek.isPresent()) {
            return candidates.stream().filter(candidate -> key(candidate).equals(key(thisWeek.get()))).findFirst();
        }
        Set<String> nudged = recent.stream().map(StreamlineNudges::key).collect(Collectors.toSet());
        Optional<StreamlineResponses.Candidate> pick = candidates.stream().filter(candidate -> !nudged.contains(key(candidate))).findFirst();
        pick.ifPresent(candidate -> nudges.save(StreamlineNudge.builder()
                .user(user)
                .anchorType(candidate.anchorType())
                .anchorId(candidate.anchorId())
                .rule(candidate.reason())
                .sentAt(now)
                .build()));
        return pick;
    }

    private static String key(StreamlineResponses.Candidate candidate) {
        return candidate.anchorType() + "-" + candidate.anchorId();
    }

    private static String key(StreamlineNudge nudge) {
        return nudge.getAnchorType() + "-" + nudge.getAnchorId();
    }
}
