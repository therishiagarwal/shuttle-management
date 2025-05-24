package com.movinsync.shuttlemanagement.repository;

import com.movinsync.shuttlemanagement.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByStudentId(Long studentId);
}
