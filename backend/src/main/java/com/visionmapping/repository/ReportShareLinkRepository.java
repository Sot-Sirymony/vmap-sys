package com.visionmapping.repository;

import com.visionmapping.entity.ReportShareLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportShareLinkRepository extends JpaRepository<ReportShareLink, Long> {

    List<ReportShareLink> findByReport_IdAndUser_IdOrderByCreatedAtDescIdDesc(Long reportId, Long userId);

    Optional<ReportShareLink> findByIdAndReport_IdAndUser_Id(Long id, Long reportId, Long userId);

    Optional<ReportShareLink> findByTokenHash(String tokenHash);
}
