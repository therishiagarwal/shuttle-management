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
}
