package com.visionmapping.repository;

import com.visionmapping.entity.Partner;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    List<Partner> findByUser_Id(Long userId);

    Page<Partner> findByUser_Id(Long userId, Pageable pageable);

    Page<Partner> findByUser_IdAndArchivedFalse(Long userId, Pageable pageable);

    List<Partner> findByUser_IdAndArchivedFalse(Long userId);

    /**
     * Free-text search across a partner's own text fields. The caller passes an
     * already lower-cased term wrapped in % wildcards.
     */
    @Query("""
            select p from Partner p
            where p.user.id = :userId
              and (:includeArchived = true or p.archived = false)
              and (lower(coalesce(p.code, '')) like :term
                or lower(coalesce(p.name, '')) like :term
                or lower(coalesce(p.role, '')) like :term
                or lower(coalesce(p.organization, '')) like :term
                or lower(coalesce(p.email, '')) like :term
                or lower(coalesce(p.phone, '')) like :term
                or lower(coalesce(p.strength, '')) like :term
                or lower(coalesce(p.notes, '')) like :term)
            """)
    Page<Partner> search(
            @Param("userId") Long userId,
            @Param("includeArchived") boolean includeArchived,
            @Param("term") String term,
            Pageable pageable);
}
