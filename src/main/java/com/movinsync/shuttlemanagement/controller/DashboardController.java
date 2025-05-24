package com.movinsync.shuttlemanagement.controller;

import com.movinsync.shuttlemanagement.service.DashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/student-overview")
    public List<Map<String, Object>> getStudentOverview() {
        return dashboardService.getStudentOverview();
    }
}
