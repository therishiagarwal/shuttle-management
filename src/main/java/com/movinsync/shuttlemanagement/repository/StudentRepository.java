package com.movinsync.shuttlemanagement.repository;

import com.movinsync.shuttlemanagement.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Student findByEmail(String email);
}
