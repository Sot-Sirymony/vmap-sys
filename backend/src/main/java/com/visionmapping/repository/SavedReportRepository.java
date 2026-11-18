package com.visionmapping.repository;

import com.visionmapping.entity.SavedReport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** V19 RM-FR-16 to RM-FR-24: a participant's saved Management Reports. */
public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {

    List<SavedReport> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    Optional<SavedReport> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_Id(Long userId);

    @Query("select r.code from SavedReport r where r.user.id = :userId")
    List<String> findCodesByUserId(@Param("userId") Long userId);
}
