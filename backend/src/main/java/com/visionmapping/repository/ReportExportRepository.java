package com.visionmapping.repository;

import com.visionmapping.entity.ReportExport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportExportRepository extends JpaRepository<ReportExport, Long> {

    List<ReportExport> findTop50ByReport_IdAndUser_IdOrderByCreatedAtDescIdDesc(Long reportId, Long userId);
}
