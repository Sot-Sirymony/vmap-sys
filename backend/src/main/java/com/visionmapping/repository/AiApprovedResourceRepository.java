package com.visionmapping.repository;

import com.visionmapping.entity.AiApprovedResource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiApprovedResourceRepository extends JpaRepository<AiApprovedResource, Long> {

    List<AiApprovedResource> findAllByOrderByNameAsc();

    List<AiApprovedResource> findByActiveTrueOrderByNameAsc();
}
