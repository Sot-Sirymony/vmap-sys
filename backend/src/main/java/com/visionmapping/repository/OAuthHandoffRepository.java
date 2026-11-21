package com.visionmapping.repository;

import com.visionmapping.entity.OAuthHandoff;
import com.visionmapping.entity.enums.OAuthHandoffKind;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** V14 (GA-FR-30): one-time hand-off and link codes. */
public interface OAuthHandoffRepository extends JpaRepository<OAuthHandoff, Long> {

    /** GA-FR-20, GA-FR-23: a code is found by its hash and its kind. */
    Optional<OAuthHandoff> findByCodeHashAndKind(String codeHash, OAuthHandoffKind kind);

    /** GA-SEC-07: uses a code once; 1 only for the first caller while it is still open. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OAuthHandoff h set h.usedAt = :now where h.id = :id and h.usedAt is null and h.expiresAt > :now")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    /** GA-FR-31: daily clean-up of codes that expired before a moment. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OAuthHandoff h where h.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
