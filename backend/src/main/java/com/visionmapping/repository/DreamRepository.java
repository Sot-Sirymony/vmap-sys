package com.visionmapping.repository;

import com.visionmapping.entity.Dream;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DreamRepository extends JpaRepository<Dream, Long>, UserScopedRepository<Dream> {

    List<Dream> findByVisionArea_IdAndUser_Id(Long visionAreaId, Long userId);

    List<Dream> findByVisionArea_IdAndUser_IdAndArchivedFalse(Long visionAreaId, Long userId);
}
