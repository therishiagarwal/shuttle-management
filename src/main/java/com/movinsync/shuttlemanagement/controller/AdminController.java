package com.movinsync.shuttlemanagement.controller;

import com.movinsync.shuttlemanagement.service.StudentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final StudentService studentService;

    public AdminController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping("/allocate-points")
    public String allocatePoints(@RequestParam Long studentId, @RequestParam int points) {
        studentService.allocatePoints(studentId, points);
        return "Allocated " + points + " points to student ID " + studentId;
    }

    @PostMapping("/deduct-points")
    public String deductPoints(@RequestParam Long studentId, @RequestParam int points) {
        studentService.deductPoints(studentId, points);
        return "Deducted " + points + " points from student ID " + studentId;
    }
}
