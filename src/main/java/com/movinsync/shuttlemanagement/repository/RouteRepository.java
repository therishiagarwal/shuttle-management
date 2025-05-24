package com.movinsync.shuttlemanagement.repository;

import com.movinsync.shuttlemanagement.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {
}
