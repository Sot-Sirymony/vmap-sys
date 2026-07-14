package com.visionmapping.repository;

import com.visionmapping.entity.CommunicationMessage;
import com.visionmapping.entity.enums.CommunicationStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunicationMessageRepository extends JpaRepository<CommunicationMessage, Long> {

    List<CommunicationMessage> findByUser_Id(Long userId);

    Page<CommunicationMessage> findByUser_Id(Long userId, Pageable pageable);

    Page<CommunicationMessage> findByUser_IdAndArchivedFalse(Long userId, Pageable pageable);

    /**
     * One query for the message list: free-text search plus the dropdown filters.
     * Every filter is optional — null means "don't filter on this" — so the page
     * contents and the total count stay correct for any combination of them.
     * Filtering has to happen here rather than in the browser because the client
     * only ever holds one page of rows.
     *
     * The caller passes an already lower-cased term wrapped in % wildcards, or
     * null for no search.
     */
    @Query("""
            select m from CommunicationMessage m
            where m.user.id = :userId
              and (:includeArchived = true or m.archived = false)
              and (:partnerId is null or m.partner.id = :partnerId)
              and (:status is null or m.status = :status)
              and (:term is null
                or lower(coalesce(m.subject, '')) like :term
                or lower(coalesce(m.audience, '')) like :term
                or lower(coalesce(m.purpose, '')) like :term
                or lower(coalesce(m.hook, '')) like :term
                or lower(coalesce(m.problem, '')) like :term
                or lower(coalesce(m.request, '')) like :term
                or lower(coalesce(m.benefitToPartner, '')) like :term
                or lower(coalesce(m.expectedOutcome, '')) like :term
                or lower(coalesce(m.messageBody, '')) like :term)
            """)
    Page<CommunicationMessage> findFiltered(
            @Param("userId") Long userId,
            @Param("includeArchived") boolean includeArchived,
            @Param("partnerId") Long partnerId,
            @Param("status") CommunicationStatus status,
            @Param("term") String term,
            Pageable pageable);
}
