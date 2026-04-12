package com.movinsync.shuttlemanagement.repository;

import com.movinsync.shuttlemanagement.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByStudentId(Long studentId);

    @Query(value = "SELECT COUNT(*) FROM trip WHERE CAST(timestamp AS DATE) = CURRENT_DATE",
            nativeQuery = true)
    long countToday();

    @Query(value = "SELECT SUM(fare_used) FROM trip " +
            "WHERE MONTH(timestamp) = MONTH(CURRENT_DATE) " +
            "AND YEAR(timestamp) = YEAR(CURRENT_DATE)",
            nativeQuery = true)
    Long totalFareUsedThisMonth();

    @Query("SELECT t FROM Trip t " +
            "WHERE t.student.id = :studentId " +
            "AND t.status <> com.movinsync.shuttlemanagement.model.TripStatus.CANCELLED " +
            "ORDER BY t.tripTime DESC")
    List<Trip> findActiveByStudentId(@Param("studentId") Long studentId);
}
