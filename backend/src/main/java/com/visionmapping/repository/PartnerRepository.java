package com.visionmapping.repository;

import com.visionmapping.entity.Partner;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.PartnerSupportType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerRepository extends JpaRepository<Partner, Long>, UserScopedRepository<Partner> {

    Page<Partner> findByUser_Id(Long userId, Pageable pageable);

    Page<Partner> findByUser_IdAndArchivedFalse(Long userId, Pageable pageable);

    /**
     * FR-55.3 / BR-44 Gate B: non-archived partners of the given support
     * types linked to the dream itself, or to one of the dream's own
     * non-archived goals. Counting the result size is the "at least two"
     * check.
     */
    @Query("""
            select p from Partner p
            left join p.relatedDream d
            left join p.relatedGoal g
            left join g.dream gd
            where p.user.id = :userId
              and p.archived = false
              and p.supportType in :supportTypes
              and ((d.id = :dreamId)
                or (gd.id = :dreamId and g.archived = false))
            """)
    List<Partner> findCounselorsForDream(
            @Param("userId") Long userId,
            @Param("dreamId") Long dreamId,
            @Param("supportTypes") List<PartnerSupportType> supportTypes);

    /**
     * One query for the partner list: free-text search plus the dropdown filters.
     * Every filter is optional — null means "don't filter on this" — so the page
     * contents and the total count stay correct for any combination of them.
     * Filtering has to happen here rather than in the browser because the client
     * only ever holds one page of rows.
     *
     * The caller passes an already lower-cased term wrapped in % wildcards, or
     * null for no search.
     */
    @Query("""
            select p from Partner p
            where p.user.id = :userId
              and (:includeArchived = true or p.archived = false)
              and (:supportType is null or p.supportType = :supportType)
              and (:status is null or p.status = :status)
              and (:dreamId is null or p.relatedDream.id = :dreamId)
              and (:term is null
                or lower(coalesce(p.code, '')) like :term
                or lower(coalesce(p.name, '')) like :term
                or lower(coalesce(p.role, '')) like :term
                or lower(coalesce(p.organization, '')) like :term
                or lower(coalesce(p.email, '')) like :term
                or lower(coalesce(p.phone, '')) like :term
                or lower(coalesce(p.strength, '')) like :term
                or lower(coalesce(p.notes, '')) like :term)
            """)
    Page<Partner> findFiltered(
            @Param("userId") Long userId,
            @Param("includeArchived") boolean includeArchived,
            @Param("supportType") PartnerSupportType supportType,
            @Param("status") PartnerStatus status,
            @Param("dreamId") Long dreamId,
            @Param("term") String term,
            Pageable pageable);
}
