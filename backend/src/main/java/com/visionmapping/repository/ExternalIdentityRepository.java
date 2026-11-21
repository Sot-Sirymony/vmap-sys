package com.visionmapping.repository;

import com.visionmapping.entity.ExternalIdentity;
import com.visionmapping.entity.enums.IdentityProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** V14 (GA-FR-28): linked outside accounts. */
public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, Long> {

    /** GA-FR-13, GA-FR-14: the Google account is found by its permanent sub. */
    Optional<ExternalIdentity> findByProviderAndSubject(IdentityProvider provider, String subject);

    Optional<ExternalIdentity> findByUser_IdAndProvider(Long userId, IdentityProvider provider);

    /** GA-FR-27, GA-FR-32: what the Account page and the data export show. */
    List<ExternalIdentity> findByUser_IdOrderByLinkedAtAsc(Long userId);
}
