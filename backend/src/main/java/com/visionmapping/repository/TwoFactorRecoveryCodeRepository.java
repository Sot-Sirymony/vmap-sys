package com.visionmapping.repository;

import com.visionmapping.entity.TwoFactorRecoveryCode;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** V21 (TF-FR-04, TF-FR-07, TF-FR-15): recovery codes. */
public interface TwoFactorRecoveryCodeRepository extends JpaRepository<TwoFactorRecoveryCode, Long> {

    /** The codes still usable for one account. */
    List<TwoFactorRecoveryCode> findByUser_IdAndUsedAtIsNull(Long userId);

    long countByUser_IdAndUsedAtIsNull(Long userId);

    /** TF-FR-15: uses a code once; 1 only for the first caller. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TwoFactorRecoveryCode c set c.usedAt = :now where c.id = :id and c.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    /** TF-FR-07: a new set replaces every old code. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TwoFactorRecoveryCode c where c.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
