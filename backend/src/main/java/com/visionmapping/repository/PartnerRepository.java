package com.visionmapping.repository;

import com.visionmapping.entity.Partner;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    List<Partner> findByUser_Id(Long userId);

    Page<Partner> findByUser_Id(Long userId, Pageable pageable);

    Page<Partner> findByUser_IdAndArchivedFalse(Long userId, Pageable pageable);

    List<Partner> findByUser_IdAndArchivedFalse(Long userId);
}
