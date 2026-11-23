package com.visionmapping.repository;

import com.visionmapping.entity.Role;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndKeyNot(String name, String key);

    List<Role> findAllByOrderByBuiltInDescNameAsc();
}
