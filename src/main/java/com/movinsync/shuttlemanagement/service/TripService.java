package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.dto.BookingResult;
import com.movinsync.shuttlemanagement.dto.ExpenseReportResult;
import com.movinsync.shuttlemanagement.dto.FrequentRouteResult;
import com.movinsync.shuttlemanagement.model.*;
import com.movinsync.shuttlemanagement.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    private final TripRepository tripRepository;
    private final StudentRepository studentRepository;
    private final StopRepository stopRepository;
    private final WalletRepository walletRepository;
    private final FareCalculatorService fareCalculatorService;

    @Value("${trip.cancellation.full-refund-minutes:10}")
    private int fullRefundMinutes;

    public TripService(TripRepository tripRepository,
                       StudentRepository studentRepository,
                       StopRepository stopRepository,
                       WalletRepository walletRepository,
                       FareCalculatorService fareCalculatorService) {
        this.tripRepository = tripRepository;
        this.studentRepository = studentRepository;
        this.stopRepository = stopRepository;
        this.walletRepository = walletRepository;
        this.fareCalculatorService = fareCalculatorService;
    }

    public BookingResult bookTrip(Long studentId, Long fromStopId, Long toStopId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        Stop from = stopRepository.findById(fromStopId)
                .orElseThrow(() -> new RuntimeException("From Stop not found"));

        Stop to = stopRepository.findById(toStopId)
                .orElseThrow(() -> new RuntimeException("To Stop not found"));

        LocalTime now     = LocalTime.now();
        boolean isPeak    = fareCalculatorService.isPeakHour(now);
        int baseFare      = fareCalculatorService.calculate(from, to, LocalTime.of(10, 0)); // off-peak base
        int finalFare     = fareCalculatorService.calculate(from, to, now);

        if (student.getWallet().getBalance() < finalFare) {
            log.warn("Booking rejected - insufficient balance: studentId={}, required={}, available={}",
                    studentId, finalFare, student.getWallet().getBalance());
            throw new RuntimeException("Insufficient wallet balance");
        }

        Wallet wallet = student.getWallet();
        wallet.setBalance(wallet.getBalance() - finalFare);
        walletRepository.save(wallet);

        Trip trip = new Trip();
        trip.setStudent(student);
        trip.setFromStop(from);
        trip.setToStop(to);
        trip.setFare(finalFare);
        trip.setTripTime(LocalDateTime.now());
        trip.setStatus(TripStatus.BOOKED);

        Trip saved = tripRepository.save(trip);
        log.info("Trip booked: tripId={}, studentId={}, from={}, to={}, fare={}, peak={}",
                saved.getId(), studentId, from.getName(), to.getName(), finalFare, isPeak);
        return new BookingResult(saved, baseFare, finalFare, isPeak);
    }

    public Trip cancelTrip(Long tripId, Long studentId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        if (!trip.getStudent().getId().equals(studentId)) {
            throw new RuntimeException("You can only cancel your own trips");
        }
        if (trip.getStatus() == TripStatus.CANCELLED) {
            log.warn("Cancel rejected - trip already cancelled: tripId={}", tripId);
            throw new RuntimeException("Trip is already cancelled");
        }
        if (trip.getStatus() == TripStatus.COMPLETED) {
            log.warn("Cancel rejected - trip already completed: tripId={}", tripId);
            throw new RuntimeException("Completed trips cannot be cancelled");
        }

        long minutesSinceBooking = ChronoUnit.MINUTES.between(trip.getTripTime(), LocalDateTime.now());
        if (minutesSinceBooking <= fullRefundMinutes) {
            Wallet wallet = trip.getStudent().getWallet();
            wallet.setBalance(wallet.getBalance() + trip.getFare());
            walletRepository.save(wallet);
            log.info("Full refund issued: tripId={}, amount={}, studentId={}", tripId, trip.getFare(), studentId);
        } else {
            log.info("Trip cancelled without refund: tripId={}, minutesSinceBooking={}", tripId, minutesSinceBooking);
        }

        trip.setStatus(TripStatus.CANCELLED);
        return tripRepository.save(trip);
    }

    public List<Trip> getTripsForStudent(Long studentId) {
        return tripRepository.findByStudentId(studentId);
    }

    public int getTotalFareSpentByStudent(Long studentId) {
        return tripRepository.findByStudentId(studentId)
                .stream()
                .mapToInt(Trip::getFare)
                .sum();
    }

    /**
     * Generates a weekly or monthly expense report for a student.
     * period = "monthly" → current calendar month
     * period = "weekly"  → last 7 days
     */
    public ExpenseReportResult getExpenseReport(Long studentId, String period) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        LocalDateTime from;
        LocalDateTime to = LocalDateTime.now();
        String label;

        if ("weekly".equalsIgnoreCase(period)) {
            from  = LocalDate.now().minusDays(6).atStartOfDay();
            label = LocalDate.now().minusDays(6) + " to " + LocalDate.now();
        } else {
            from  = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            label = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }

        final LocalDateTime start = from;

        List<Trip> trips = tripRepository.findActiveByStudentId(studentId)
                .stream()
                .filter(t -> !t.getTripTime().isBefore(start) && !t.getTripTime().isAfter(to))
                .toList();

        int totalFare = trips.stream().mapToInt(Trip::getFare).sum();

        List<ExpenseReportResult.TripSummary> summaries = trips.stream()
                .map(t -> new ExpenseReportResult.TripSummary(
                        t.getTripTime().toLocalDate().toString(),
                        t.getFromStop().getName(),
                        t.getToStop().getName(),
                        t.getFare(),
                        t.getStatus().name()
                ))
                .toList();

        return new ExpenseReportResult(
                label,
                totalFare,
                trips.size(),
                student.getWallet().getBalance(),
                summaries
        );
    }

    /**
     * Returns top N most frequently booked (fromStop, toStop) pairs for a student,
     * sorted descending by trip count.
     *
     * Time  : O(t log t) — t = student's trip count
     * Space : O(k)       — k = distinct route pairs
     */
    public List<FrequentRouteResult> getFrequentRoutes(Long studentId, int limit) {
        List<Trip> trips = tripRepository.findActiveByStudentId(studentId);

        if (trips.isEmpty()) return List.of();

        // Group by "fromStopName -> toStopName" key
        Map<String, List<Trip>> grouped = trips.stream()
                .collect(Collectors.groupingBy(t ->
                        t.getFromStop().getName() + "|" + t.getToStop().getName()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<Trip> group   = entry.getValue();
                    String[]   parts   = entry.getKey().split("\\|");
                    LocalDateTime last = group.stream()
                            .map(Trip::getTripTime)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                    return new FrequentRouteResult(parts[0], parts[1], group.size(), last);
                })
                .sorted(Comparator.comparingLong(FrequentRouteResult::getTripCount).reversed())
                .limit(limit)
                .toList();
    }
}
