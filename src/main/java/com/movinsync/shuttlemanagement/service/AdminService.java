package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.repository.*;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AdminService {

    private final StudentRepository studentRepository;
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final TripRepository tripRepository;
    private final WalletRepository walletRepository;

    public AdminService(StudentRepository studentRepository,
                        RouteRepository routeRepository,
                        StopRepository stopRepository,
                        TripRepository tripRepository,
                        WalletRepository walletRepository) {
        this.studentRepository = studentRepository;
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.tripRepository = tripRepository;
        this.walletRepository = walletRepository;
    }

    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalStudents", studentRepository.count());
        stats.put("totalRoutes", routeRepository.count());
        stats.put("totalStops", stopRepository.count());
        stats.put("totalTripsToday", tripRepository.countToday());
        
        // ✅ Fix: Add null checks
        Long pointsGiven = walletRepository.totalDistributedThisMonth();
        stats.put("pointsDistributedThisMonth", pointsGiven != null ? pointsGiven : 0);
        
        Long pointsUsed = tripRepository.totalFareUsedThisMonth();
        stats.put("pointsUsedThisMonth", pointsUsed != null ? pointsUsed : 0);
        
        return stats;

    }
}
