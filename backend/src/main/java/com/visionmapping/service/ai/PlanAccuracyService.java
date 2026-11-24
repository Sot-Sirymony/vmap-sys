package com.visionmapping.service.ai;

import com.visionmapping.dto.response.AiPlanResponses.PlanAccuracyMetrics;
import com.visionmapping.dto.response.AiPlanResponses.RuleAccuracy;
import com.visionmapping.entity.AiOwnerStrip;
import com.visionmapping.entity.PlanCheckEvent;
import com.visionmapping.repository.AiOwnerStripRepository;
import com.visionmapping.repository.PlanCheckEventRepository;
import com.visionmapping.security.Permission;
import com.visionmapping.security.PermissionService;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * V31 (section 7, DC-FR-64, DC-FR-65): the counts behind the accuracy metrics.
 * One row per rule for each check and each save, and one row per AI answer
 * whose owners were checked. Never any text (DC-NFR-04).
 */
@Service
@RequiredArgsConstructor
public class PlanAccuracyService {

    private final PlanCheckEventRepository events;
    private final AiOwnerStripRepository strips;
    private final Clock clock;
    private final EntityLookup lookup;
    private final PermissionService permissions;

    /** A check run on a draft; the first one of a session feeds DCM-01 and DCM-02. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCheck(Long userId, Long dreamId, @Nullable String sessionKey, PlanRuleService.Report report) {
        boolean first = sessionKey != null && !events.existsBySessionKeyAndAtSaveFalse(sessionKey);
        events.saveAll(rows(userId, dreamId, sessionKey, report, first, false, Set.of()));
    }

    /** A saved plan, after the server re-ran the rules, with the SHOULD rules the participant accepted. */
    @Transactional
    public void recordSave(Long userId, Long dreamId, @Nullable String sessionKey, PlanRuleService.Report report, Set<String> accepted) {
        events.saveAll(rows(userId, dreamId, sessionKey, report, false, true, accepted));
    }

    /** R-07: how many owners an AI answer gave and how many were removed. Nothing is written when it gave none. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOwners(Long userId, String feature, int seen, int stripped) {
        if (seen == 0) {
            return;
        }
        strips.save(AiOwnerStrip.builder().userId(userId).feature(feature).ownersSeen(seen).ownersStripped(stripped)
                .createdAt(clock.instant()).build());
    }

    /** DC-FR-65: DCM-01, DCM-02, DCM-03, DCM-06 and DCM-07 over the last days, for admins only. */
    @Transactional(readOnly = true)
    public PlanAccuracyMetrics metrics(int days) {
        permissions.require(lookup.currentUser(), Permission.VIEW_AI_REPORTS, "Only an administrator can see AI plan builder metrics.");
        int period = Math.max(1, Math.min(days, AiPlanFeedbackService.MAX_METRIC_DAYS));
        Instant from = clock.instant().minus(Duration.ofDays(period));
        long checked = events.sessions(from, true, false);
        long saved = events.sessions(from, false, true);
        Map<String, Object[]> firstRuns = byRule(events.ruleTotals(from, true, false));
        Map<String, Object[]> saves = byRule(events.ruleTotals(from, false, true));
        List<RuleAccuracy> rules = new ArrayList<>();
        for (String rule : PlanRuleService.RULES) {
            Object[] first = firstRuns.get(rule);
            Object[] save = saves.get(rule);
            rules.add(new RuleAccuracy(rule, number(first, 1), rate(number(first, 2), number(first, 1)), number(save, 1),
                    number(save, 5)));
        }
        Object[] dates = firstRuns.get("R-04");
        Object[] owners = strips.totalsSince(from).get(0);
        long seen = number(owners, 0);
        return new PlanAccuracyMetrics(period, checked, rate(events.cleanSessions(from, true, false), checked), rules, saved,
                rate(events.cleanSessions(from, false, true), saved), number(dates, 3), rate(number(dates, 4), number(dates, 3)),
                seen, rate(number(owners, 1), seen));
    }

    private static Map<String, Object[]> byRule(List<Object[]> rows) {
        Map<String, Object[]> map = new HashMap<>();
        rows.forEach(row -> map.put((String) row[0], row));
        return map;
    }

    private static long number(@Nullable Object[] row, int index) {
        return row == null || row[index] == null ? 0 : ((Number) row[index]).longValue();
    }

    @Nullable
    private static Double rate(long part, long whole) {
        return whole == 0 ? null : Math.round(part * 1000.0 / whole) / 1000.0;
    }

    private List<PlanCheckEvent> rows(Long userId, Long dreamId, @Nullable String sessionKey, PlanRuleService.Report report,
                                      boolean first, boolean atSave, Set<String> accepted) {
        Instant now = clock.instant();
        List<PlanCheckEvent> rows = new ArrayList<>();
        for (Map.Entry<String, PlanRuleService.RuleStat> entry : report.stats().entrySet()) {
            PlanRuleService.RuleStat stat = entry.getValue();
            rows.add(PlanCheckEvent.builder().userId(userId).dreamId(dreamId).sessionKey(sessionKey).rule(entry.getKey())
                    .mustFails(stat.must()).shouldFails(stat.should()).firstRun(first).atSave(atSave)
                    .accepted(stat.should() > 0 && accepted.contains(entry.getKey()))
                    .items(stat.items()).aiItems(stat.aiItems()).aiFails(stat.aiFails()).createdAt(now).build());
        }
        return rows;
    }
}
