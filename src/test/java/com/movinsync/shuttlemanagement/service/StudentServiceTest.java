package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.model.Role;
import com.movinsync.shuttlemanagement.model.Student;
import com.movinsync.shuttlemanagement.model.Wallet;
import com.movinsync.shuttlemanagement.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private StudentService studentService;

    private Wallet wallet;
    private Student student;

    @BeforeEach
    void setUp() {
        wallet  = new Wallet(1L, 200);
        student = new Student(1L, "Bob", "bob@university.edu", "rawPassword", Role.STUDENT, wallet);
    }

    // ── createStudent ─────────────────────────────────────────────────────────

    @Test
    void createStudent_encodesPasswordBeforeSave() {
        when(passwordEncoder.encode("rawPassword")).thenReturn("encodedPassword");
        when(studentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Student result = studentService.createStudent(student);

        assertThat(result.getPassword()).isEqualTo("encodedPassword");
        verify(passwordEncoder).encode("rawPassword");
        verify(studentRepository).save(student);
    }

    @Test
    void createStudent_returnsPersistedStudent() {
        Student persisted = new Student(42L, "Bob", "bob@university.edu", "encodedPassword", Role.STUDENT, wallet);
        when(passwordEncoder.encode(any())).thenReturn("encodedPassword");
        when(studentRepository.save(any())).thenReturn(persisted);

        Student result = studentService.createStudent(student);

        assertThat(result.getId()).isEqualTo(42L);
    }

    // ── allocatePoints ────────────────────────────────────────────────────────

    @Test
    void allocatePoints_increasesWalletBalance() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        studentService.allocatePoints(1L, 50);

        assertThat(wallet.getBalance()).isEqualTo(250);
        verify(studentRepository).save(student);
    }

    @Test
    void allocatePoints_studentNotFound_throwsRuntimeException() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.allocatePoints(99L, 50))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Student not found");

        verify(studentRepository, never()).save(any());
    }

    // ── deductPoints ──────────────────────────────────────────────────────────

    @Test
    void deductPoints_decreasesWalletBalance() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        studentService.deductPoints(1L, 30);

        assertThat(wallet.getBalance()).isEqualTo(170);
        verify(studentRepository).save(student);
    }

    @Test
    void deductPoints_studentNotFound_throwsRuntimeException() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.deductPoints(99L, 30))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Student not found");

        verify(studentRepository, never()).save(any());
    }
}
