package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.model.Student;
import com.movinsync.shuttlemanagement.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public StudentService(StudentRepository repo, PasswordEncoder passwordEncoder) {
        this.studentRepository = repo;
        this.passwordEncoder = passwordEncoder;
    }

    public Student createStudent(Student student) {
        student.setPassword(passwordEncoder.encode(student.getPassword()));
        Student saved = studentRepository.save(student);
        log.info("Student created: studentId={}, email={}", saved.getId(), saved.getEmail());
        return saved;
    }

    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    public void allocatePoints(Long studentId, int points) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        student.getWallet().setBalance(student.getWallet().getBalance() + points);
        studentRepository.save(student);
        log.info("Points allocated: studentId={}, points={}, newBalance={}", studentId, points, student.getWallet().getBalance());
    }

    public void deductPoints(Long studentId, int points) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        student.getWallet().setBalance(student.getWallet().getBalance() - points);
        studentRepository.save(student);
        log.info("Points deducted: studentId={}, points={}, newBalance={}", studentId, points, student.getWallet().getBalance());
    }
}
