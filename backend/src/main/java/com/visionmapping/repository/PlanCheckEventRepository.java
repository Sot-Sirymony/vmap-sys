package com.visionmapping.repository;

import com.visionmapping.entity.PlanCheckEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlanCheckEventRepository extends JpaRepository<PlanCheckEvent, Long> {

    /** V31: whether a draft session has run the check before (DCM-01 counts only its first run). */
    boolean existsBySessionKeyAndAtSaveFalse(String sessionKey);

    /**
     * V31 DCM-02, DCM-06: per rule, since a time and for first runs or saves:
     * runs, runs with no Must fail, AI items and AI items failing, and Should fails accepted.
     */
    @Query("""
            select event.rule, count(event),
                   sum(case when event.mustFails = 0 then 1 else 0 end),
                   coalesce(sum(event.aiItems), 0), coalesce(sum(event.aiFails), 0),
                   sum(case when event.accepted then 1 else 0 end)
            from PlanCheckEvent event
            where event.createdAt >= :from and event.firstRun = :firstRun and event.atSave = :atSave
            group by event.rule order by event.rule
            """)
    List<Object[]> ruleTotals(Instant from, boolean firstRun, boolean atSave);

    /** V31 DCM-01, DCM-03: sessions, and sessions with no Must fail in any rule, for first runs or saves. */
    @Query("""
            select count(distinct event.sessionKey) from PlanCheckEvent event
            where event.createdAt >= :from and event.firstRun = :firstRun and event.atSave = :atSave
            """)
    long sessions(Instant from, boolean firstRun, boolean atSave);

    @Query("""
            select count(distinct event.sessionKey) from PlanCheckEvent event
            where event.createdAt >= :from and event.firstRun = :firstRun and event.atSave = :atSave
              and event.sessionKey not in (select failed.sessionKey from PlanCheckEvent failed
                  where failed.createdAt >= :from and failed.firstRun = :firstRun and failed.atSave = :atSave
                    and failed.mustFails > 0 and failed.sessionKey is not null)
            """)
    long cleanSessions(Instant from, boolean firstRun, boolean atSave);
}
