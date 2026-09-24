package com.visionmapping.repository;

import com.visionmapping.entity.VisionArea;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisionAreaRepository extends JpaRepository<VisionArea, Long>, CodedRepository<VisionArea> {

    /** Next sort position among the user's areas: a count, not a load of every area. */
    long countByUser_IdAndArchivedFalse(Long userId);
}
