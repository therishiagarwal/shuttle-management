package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.model.*;
import com.movinsync.shuttlemanagement.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final StudentRepository studentRepository;
    private final StopRepository stopRepository;
    private final WalletRepository walletRepository;
    private final FareCalculatorService fareCalculatorService;

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

    public Trip bookTrip(Long studentId, Long fromStopId, Long toStopId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        Stop from = stopRepository.findById(fromStopId)
                .orElseThrow(() -> new RuntimeException("From Stop not found"));

        Stop to = stopRepository.findById(toStopId)
                .orElseThrow(() -> new RuntimeException("To Stop not found"));

        int fare = fareCalculatorService.calculate(from, to);

        if (student.getWallet().getBalance() < fare) {
            throw new RuntimeException("Insufficient wallet balance");
        }

        Wallet wallet = student.getWallet();
        wallet.setBalance(wallet.getBalance() - fare);
        walletRepository.save(wallet);

        Trip trip = new Trip();
        trip.setStudent(student);
        trip.setFromStop(from);
        trip.setToStop(to);
        trip.setFare(fare);
        trip.setTripTime(LocalDateTime.now());

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
}
