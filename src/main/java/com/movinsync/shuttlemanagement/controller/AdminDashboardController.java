package com.movinsync.shuttlemanagement.controller;

import com.movinsync.shuttlemanagement.service.AdminService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminService adminService;

    public AdminDashboardController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/summary")
    public Map<String, Object> getDashboardSummary() {
        return adminService.getDashboardSummary();
    }
}
