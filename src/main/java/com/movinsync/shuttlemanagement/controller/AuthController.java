package com.movinsync.shuttlemanagement.controller;

import com.movinsync.shuttlemanagement.dto.LoginRequest;
import com.movinsync.shuttlemanagement.model.Student;
import com.movinsync.shuttlemanagement.repository.StudentRepository;
import com.movinsync.shuttlemanagement.util.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final StudentRepository studentRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthController(StudentRepository studentRepository, JwtUtil jwtUtil,
                          PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        Student student = studentRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        if (!passwordEncoder.matches(request.getPassword(), student.getPassword())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        Map<String, String> response = new HashMap<>();
        response.put("token", jwtUtil.generateToken(student.getEmail(), student.getRole().name()));
        return ResponseEntity.ok(response);
    }
}
