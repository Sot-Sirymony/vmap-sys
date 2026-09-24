package com.visionmapping.repository;

import com.visionmapping.entity.ProgressLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgressLogRepository extends JpaRepository<ProgressLog, Long>, UserScopedRepository<ProgressLog> {

    List<ProgressLog> findByRelatedTask_IdAndUser_Id(Long taskId, Long userId);

    /**
     * Only the log rows the dashboard's progress trend can use: everything from
     * the window start onward, plus, for each task, its single latest entry from
     * before the window. That last one matters because a task's progress carries
     * forward until it next changes, so it is the value the trend starts from.
     * Older history can never be "the latest as of" a day inside the window, so
     * it is not loaded. The log table grows on every task edit; this keeps the
     * dashboard's read from growing with it.
     */
    @Query("""
            select l from ProgressLog l
            where l.user.id = :userId and l.archived = false
              and (l.loggedAt >= :windowStart
                   or l.loggedAt = (select max(p.loggedAt) from ProgressLog p
                                    where p.relatedTask = l.relatedTask
                                      and p.archived = false
                                      and p.loggedAt < :windowStart))
            """)
    List<ProgressLog> findTrendWindow(@Param("userId") Long userId, @Param("windowStart") Instant windowStart);
}
