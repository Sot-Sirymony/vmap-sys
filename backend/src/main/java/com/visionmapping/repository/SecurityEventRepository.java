package com.visionmapping.repository;

import com.visionmapping.entity.SecurityEvent;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import java.time.Instant;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * V10 (SEC-09-03): deliberately not a JpaRepository. It can add rows and read
 * them, and the only removal is by age for retention; there is no update and no
 * delete by id, so no code path can rewrite the log.
 */
public interface SecurityEventRepository extends Repository<SecurityEvent, Long> {

    SecurityEvent save(SecurityEvent event);

    @Query("select e from SecurityEvent e where (:type is null or e.eventType = :type) "
            + "and (:outcome is null or e.outcome = :outcome) and (:actorId is null or e.actorId = :actorId) "
            + "and e.occurredAt >= :from and e.occurredAt < :to order by e.occurredAt desc, e.id desc")
    Page<SecurityEvent> search(@Param("type") SecurityEventType type, @Param("outcome") SecurityOutcome outcome,
                               @Param("actorId") Long actorId, @Param("from") Instant from, @Param("to") Instant to,
                               Pageable pageable);

    long countByEventTypeInAndSubjectHashAndOccurredAtAfter(Collection<SecurityEventType> types, String subjectHash, Instant after);

    long countByEventTypeInAndIpAddressAndOccurredAtAfter(Collection<SecurityEventType> types, String ipAddress, Instant after);

    long countByEventTypeAndActorIdAndOccurredAtAfter(SecurityEventType type, Long actorId, Instant after);

    /** V10 SEC-09-04: whether this AI budget warning (by its detail prefix) was already raised. */
    boolean existsByEventTypeAndDetailStartingWith(SecurityEventType type, String detailPrefix);

    @Modifying
    @Query("delete from SecurityEvent e where e.occurredAt < :before")
    int deleteOlderThan(@Param("before") Instant before);

    /** V23 (UM-FR-19): events by or about one account, newest first. */
    @Query("select e from SecurityEvent e where e.actorId = :userId or (e.targetType = 'user' and e.targetId = :userId) order by e.occurredAt desc, e.id desc")
    java.util.List<com.visionmapping.entity.SecurityEvent> findAboutUser(@Param("userId") Long userId, org.springframework.data.domain.Pageable page);
}
