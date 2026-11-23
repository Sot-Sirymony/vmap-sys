package com.visionmapping.repository;

import com.visionmapping.entity.UserInvitation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, Long> {

    Optional<UserInvitation> findByTokenHash(String tokenHash);

    List<UserInvitation> findByUser_IdAndUsedAtIsNull(Long userId);

    /** Whether the person still has a link that works (the detail panel, Google acceptance). */
    Optional<UserInvitation> findFirstByUser_IdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(Long userId, Instant now);

    boolean existsByUser_Id(Long userId);
}
