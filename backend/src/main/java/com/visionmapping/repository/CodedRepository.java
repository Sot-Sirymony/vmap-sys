package com.visionmapping.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

/**
 * User-scoped repositories whose entity carries a display code such as
 * "G-007". Generating the next code only needs the existing codes, so this
 * reads one narrow column instead of loading every entity the user owns.
 */
@NoRepositoryBean
public interface CodedRepository<T> extends UserScopedRepository<T> {

    /** Every code the user has ever issued for this entity, archived rows included. */
    @Query("select e.code from #{#entityName} e where e.user.id = :userId")
    List<String> findCodesByUserId(@Param("userId") Long userId);
}
