package com.visionmapping.repository;

import java.util.List;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The two user-scoped finders every archivable entity shares. Repositories
 * extend this instead of redeclaring them, so the derived-query names stay
 * identical across the app and services can list any entity through one
 * shared helper.
 */
@NoRepositoryBean
public interface UserScopedRepository<T> extends Repository<T, Long> {

    List<T> findByUser_Id(Long userId);

    List<T> findByUser_IdAndArchivedFalse(Long userId);

    /** V33 (D-33-9): one page of the user's records, archived ones included. */
    Page<T> findByUser_Id(Long userId, Pageable pageable);

    /** V33 (D-33-9): one page of the user's active records. */
    Page<T> findByUser_IdAndArchivedFalse(Long userId, Pageable pageable);
}
