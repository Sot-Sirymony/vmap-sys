package com.visionmapping.repository;

import com.visionmapping.entity.AiOwnerStrip;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiOwnerStripRepository extends JpaRepository<AiOwnerStrip, Long> {

    /** V31 DCM-07: owners Claude gave, and owners removed, since a time. */
    @Query("""
            select coalesce(sum(strip.ownersSeen), 0), coalesce(sum(strip.ownersStripped), 0)
            from AiOwnerStrip strip where strip.createdAt >= :from
            """)
    List<Object[]> totalsSince(Instant from);
}
