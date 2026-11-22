package com.visionmapping.repository;

import com.visionmapping.entity.TrustedDevice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** V21 (TF-FR-21 to TF-FR-26): trusted devices. */
public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {

    Optional<TrustedDevice> findByTokenHash(String tokenHash);

    /** TF-FR-23: the devices still remembered for one account, newest use first. */
    List<TrustedDevice> findByUser_IdAndRemovedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(Long userId, Instant now);

    /** TF-FR-26: the oldest-used remembered devices beyond the limit. */
    List<TrustedDevice> findByUser_IdAndRemovedAtIsNullOrderByLastUsedAtAsc(Long userId);

    /** TF-FR-24: removes every device for one account at once. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TrustedDevice d set d.removedAt = :now where d.user.id = :userId and d.removedAt is null")
    int removeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /** Daily clean-up of devices that expired or were removed before a moment. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TrustedDevice d where d.expiresAt < :before or d.removedAt < :before")
    int deleteEndedBefore(@Param("before") Instant before);
}
