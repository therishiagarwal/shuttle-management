package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.model.Student;
import com.movinsync.shuttlemanagement.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository repo) {
        this.studentRepository = repo;
    }

    public Student createStudent(Student student) {
        return studentRepository.save(student);
    }

    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }
    //admin functionality to allocate and deduct points from student wallets

    public void allocatePoints(Long studentId, int points) {
    Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));

    int currentBalance = student.getWallet().getBalance();
    student.getWallet().setBalance(currentBalance + points);
    studentRepository.save(student);
}

public void deductPoints(Long studentId, int points) {
    Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));

    int currentBalance = student.getWallet().getBalance();
    student.getWallet().setBalance(currentBalance - points);
    studentRepository.save(student);
}

}
