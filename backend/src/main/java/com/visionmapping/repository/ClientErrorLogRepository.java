package com.visionmapping.repository;

import com.visionmapping.entity.ClientErrorLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Deliberately not a JpaRepository: rows are added and read, never changed.
 * They go only with the account (AccountService deletes by user_id).
 */
public interface ClientErrorLogRepository extends Repository<ClientErrorLog, Long> {

    ClientErrorLog save(ClientErrorLog log);

    List<ClientErrorLog> findTop50ByUserIdAndPageOrderByOccurredAtDescIdDesc(Long userId, String page);

    long countByUserIdAndOccurredAtAfter(Long userId, Instant after);

    @Query("select l from ClientErrorLog l where (:page is null or l.page = :page) "
            + "and (:status is null or l.status = :status) order by l.occurredAt desc, l.id desc")
    Page<ClientErrorLog> search(@Param("page") String page, @Param("status") Integer status, Pageable pageable);
}
