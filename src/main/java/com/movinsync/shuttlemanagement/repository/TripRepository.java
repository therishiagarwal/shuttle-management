package com.movinsync.shuttlemanagement.repository;

import com.movinsync.shuttlemanagement.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByStudentId(Long studentId);

    @Query(value = "SELECT COUNT(*) FROM trip WHERE CAST(timestamp AS DATE) = CURRENT_DATE", nativeQuery = true)
    long countToday();

    
    @Query(value = "SELECT SUM(fare_used) FROM trip WHERE MONTH(timestamp) = MONTH(CURRENT_DATE) AND YEAR(timestamp) = YEAR(CURRENT_DATE)", nativeQuery = true)
    Long totalFareUsedThisMonth();

}
