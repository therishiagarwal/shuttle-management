package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.dto.BookingResult;
import com.movinsync.shuttlemanagement.model.*;
import com.movinsync.shuttlemanagement.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private StopRepository stopRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private FareCalculatorService fareCalculatorService;

    @InjectMocks
    private TripService tripService;

    private Student student;
    private Wallet wallet;
    private Stop fromStop;
    private Stop toStop;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tripService, "fullRefundMinutes", 10);
        wallet   = new Wallet(1L, 100);
        student  = new Student(1L, "Alice", "alice@university.edu", "hashed", Role.STUDENT, wallet);
        fromStop = new Stop(1L, "Stop A", 12.9716, 77.5946);
        toStop   = new Stop(2L, "Stop B", 12.9352, 77.6245);
    }

    // ── bookTrip ─────────────────────────────────────────────────────────────

    @Test
    void bookTrip_success_deductsWalletAndReturnsResult() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(stopRepository.findById(1L)).thenReturn(Optional.of(fromStop));
        when(stopRepository.findById(2L)).thenReturn(Optional.of(toStop));
        when(fareCalculatorService.isPeakHour(any())).thenReturn(false);
        when(fareCalculatorService.calculate(any(), any(), any())).thenReturn(10);

        Trip saved = new Trip(10L, student, fromStop, toStop, 10, LocalDateTime.now(), TripStatus.BOOKED);
        when(tripRepository.save(any())).thenReturn(saved);

        BookingResult result = tripService.bookTrip(1L, 1L, 2L);

        assertThat(result).isNotNull();
        assertThat(result.getFinalFare()).isEqualTo(10);
        assertThat(wallet.getBalance()).isEqualTo(90);
        verify(walletRepository).save(wallet);
        verify(tripRepository).save(any(Trip.class));
    }

    @Test
    void bookTrip_peakHour_reflectsInResult() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(stopRepository.findById(1L)).thenReturn(Optional.of(fromStop));
        when(stopRepository.findById(2L)).thenReturn(Optional.of(toStop));
        when(fareCalculatorService.isPeakHour(any())).thenReturn(true);
        when(fareCalculatorService.calculate(any(), any(), any())).thenReturn(15);

        Trip saved = new Trip(10L, student, fromStop, toStop, 15, LocalDateTime.now(), TripStatus.BOOKED);
        when(tripRepository.save(any())).thenReturn(saved);

        BookingResult result = tripService.bookTrip(1L, 1L, 2L);

        assertThat(result.isPeakHour()).isTrue();
        assertThat(wallet.getBalance()).isEqualTo(85);
    }

    @Test
    void bookTrip_insufficientBalance_throwsRuntimeException() {
        wallet.setBalance(5);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(stopRepository.findById(1L)).thenReturn(Optional.of(fromStop));
        when(stopRepository.findById(2L)).thenReturn(Optional.of(toStop));
        when(fareCalculatorService.isPeakHour(any())).thenReturn(false);
        when(fareCalculatorService.calculate(any(), any(), any())).thenReturn(10);

        assertThatThrownBy(() -> tripService.bookTrip(1L, 1L, 2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient wallet balance");

        verify(tripRepository, never()).save(any());
    }

    @Test
    void bookTrip_studentNotFound_throwsRuntimeException() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.bookTrip(99L, 1L, 2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Student not found");
    }

    @Test
    void bookTrip_stopNotFound_throwsRuntimeException() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(stopRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.bookTrip(1L, 99L, 2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Stop not found");
    }

    // ── cancelTrip ───────────────────────────────────────────────────────────

    @Test
    void cancelTrip_withinRefundWindow_issuesFullRefund() {
        Trip trip = new Trip(5L, student, fromStop, toStop, 20, LocalDateTime.now().minusMinutes(3), TripStatus.BOOKED);
        when(tripRepository.findById(5L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(trip)).thenReturn(trip);

        Trip result = tripService.cancelTrip(5L, 1L);

        assertThat(result.getStatus()).isEqualTo(TripStatus.CANCELLED);
        assertThat(wallet.getBalance()).isEqualTo(120); // 100 + 20 refund
        verify(walletRepository).save(wallet);
    }

    @Test
    void cancelTrip_outsideRefundWindow_noRefund() {
        Trip trip = new Trip(5L, student, fromStop, toStop, 20, LocalDateTime.now().minusMinutes(15), TripStatus.BOOKED);
        when(tripRepository.findById(5L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(trip)).thenReturn(trip);

        Trip result = tripService.cancelTrip(5L, 1L);

        assertThat(result.getStatus()).isEqualTo(TripStatus.CANCELLED);
        assertThat(wallet.getBalance()).isEqualTo(100); // unchanged
        verify(walletRepository, never()).save(any());
    }

    @Test
    void cancelTrip_alreadyCancelled_throwsRuntimeException() {
        Trip trip = new Trip(5L, student, fromStop, toStop, 20, LocalDateTime.now(), TripStatus.CANCELLED);
        when(tripRepository.findById(5L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.cancelTrip(5L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already cancelled");
    }

    @Test
    void cancelTrip_completedTrip_throwsRuntimeException() {
        Trip trip = new Trip(5L, student, fromStop, toStop, 20, LocalDateTime.now(), TripStatus.COMPLETED);
        when(tripRepository.findById(5L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.cancelTrip(5L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    @Test
    void cancelTrip_wrongStudent_throwsRuntimeException() {
        Trip trip = new Trip(5L, student, fromStop, toStop, 20, LocalDateTime.now(), TripStatus.BOOKED);
        when(tripRepository.findById(5L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.cancelTrip(5L, 99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("only cancel your own trips");
    }
}
