package com.movinsync.shuttlemanagement.repository;

import com.movinsync.shuttlemanagement.model.Stop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StopRepository extends JpaRepository<Stop, Long> {
}
