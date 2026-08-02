package com.visionmapping.repository;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.PasswordResetToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Lookup is by hash because the plain token is never stored. The value from
     * the emailed link is hashed and matched against this column.
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Whatever the user still has outstanding, so issuing a new link can retire
     * the previous one. Requesting a reset twice must not leave two working
     * links in two different inboxes.
     */
    List<PasswordResetToken> findByUserAndUsedAtIsNull(AppUser user);

    /** Housekeeping: spent and aged-out rows have no further purpose. */
    long deleteByExpiresAtBefore(Instant cutoff);
}
