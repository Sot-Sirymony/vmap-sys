package com.visionmapping.repository;

import com.visionmapping.entity.AttitudeCheckIn;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttitudeCheckInRepository extends JpaRepository<AttitudeCheckIn, Long> {

    Optional<AttitudeCheckIn> findByUser_IdAndCheckInDate(Long userId, LocalDate checkInDate);

    List<AttitudeCheckIn> findTop30ByUser_IdOrderByCheckInDateDesc(Long userId);

    boolean existsByUser_Id(Long userId);

    /** FR-77: the last time the user checked in. */
    @org.springframework.data.jpa.repository.Query("select max(a.updatedAt) from AttitudeCheckIn a where a.user.id = :userId")
    java.time.Instant findLastUpdatedAt(@org.springframework.data.repository.query.Param("userId") Long userId);

    List<AttitudeCheckIn> findByUser_IdOrderByCheckInDateAsc(Long userId);
}
