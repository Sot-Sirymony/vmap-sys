package com.visionmapping.repository;

import com.visionmapping.entity.ReportCoachShare;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** V19 RM-FR-50: reports shared with a coach. */
public interface ReportCoachShareRepository extends JpaRepository<ReportCoachShare, Long> {

    Optional<ReportCoachShare> findByReport_IdAndUser_Id(Long reportId, Long userId);

    Optional<ReportCoachShare> findByReport_IdAndUser_IdAndCoach_Id(Long reportId, Long userId, Long coachId);

    List<ReportCoachShare> findByUser_IdAndCoach_IdOrderByCreatedAtDescIdDesc(Long userId, Long coachId);

    /** Unshare at once (RM-FR-50), on archive (RM-FR-51), or when the participant leaves their cohort. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ReportCoachShare s where s.report.id = :reportId")
    void deleteByReportId(@Param("reportId") Long reportId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ReportCoachShare s where s.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
