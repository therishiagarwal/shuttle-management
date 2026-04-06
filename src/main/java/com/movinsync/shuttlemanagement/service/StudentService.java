package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.model.Student;
import com.movinsync.shuttlemanagement.repository.StudentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public StudentService(StudentRepository repo, PasswordEncoder passwordEncoder) {
        this.studentRepository = repo;
        this.passwordEncoder = passwordEncoder;
    }

    public Student createStudent(Student student) {
        student.setPassword(passwordEncoder.encode(student.getPassword()));
        return studentRepository.save(student);
    }

    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    public void allocatePoints(Long studentId, int points) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        student.getWallet().setBalance(student.getWallet().getBalance() + points);
        studentRepository.save(student);
    }

    public void deductPoints(Long studentId, int points) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        student.getWallet().setBalance(student.getWallet().getBalance() - points);
        studentRepository.save(student);
    }
}
