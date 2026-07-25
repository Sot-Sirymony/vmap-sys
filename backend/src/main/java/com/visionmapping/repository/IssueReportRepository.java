package com.visionmapping.repository;

import com.visionmapping.entity.IssueReport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueReportRepository
        extends JpaRepository<IssueReport, Long>, UserScopedRepository<IssueReport> {

    /** Admin triage (FR-38.4): the whole queue, newest first. */
    List<IssueReport> findAllByOrderByCreatedAtDesc();
}
