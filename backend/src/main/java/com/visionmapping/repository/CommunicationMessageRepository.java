package com.visionmapping.repository;

import com.visionmapping.entity.CommunicationMessage;
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
     * Free-text search across a message's own text fields. The caller passes an
     * already lower-cased term wrapped in % wildcards.
     */
    @Query("""
            select m from CommunicationMessage m
            where m.user.id = :userId
              and (:includeArchived = true or m.archived = false)
              and (lower(coalesce(m.subject, '')) like :term
                or lower(coalesce(m.audience, '')) like :term
                or lower(coalesce(m.purpose, '')) like :term
                or lower(coalesce(m.hook, '')) like :term
                or lower(coalesce(m.problem, '')) like :term
                or lower(coalesce(m.request, '')) like :term
                or lower(coalesce(m.benefitToPartner, '')) like :term
                or lower(coalesce(m.expectedOutcome, '')) like :term
                or lower(coalesce(m.messageBody, '')) like :term)
            """)
    Page<CommunicationMessage> search(
            @Param("userId") Long userId,
            @Param("includeArchived") boolean includeArchived,
            @Param("term") String term,
            Pageable pageable);
}
