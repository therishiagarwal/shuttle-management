package com.movinsync.shuttlemanagement.controller;

import com.movinsync.shuttlemanagement.model.Student;
import com.movinsync.shuttlemanagement.repository.StudentRepository;
import com.movinsync.shuttlemanagement.util.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final StudentRepository studentRepository;
    private final JwtUtil jwtUtil;

    public AuthController(StudentRepository studentRepository, JwtUtil jwtUtil) {
        this.studentRepository = studentRepository;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestParam String email) {
        System.out.println("Login request received for: " + email);
        
        Student student = studentRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        
        System.out.println("Student found: " + student.getName());
        
        // TEMP: Replace token generation to isolate issue
        // String token = "dummy-token";
        String token = jwtUtil.generateToken(student.getEmail());
        System.out.println("Token generated.");
        
        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        return response;
}

}
