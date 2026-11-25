package com.visionmapping.repository;

import com.visionmapping.entity.StreamlineApplyBatchEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreamlineApplyBatchEntryRepository extends JpaRepository<StreamlineApplyBatchEntry, Long> {

    List<StreamlineApplyBatchEntry> findByBatch_IdOrderByPositionAsc(Long batchId);

    List<StreamlineApplyBatchEntry> findByBatch_IdOrderByPositionDesc(Long batchId);
}
