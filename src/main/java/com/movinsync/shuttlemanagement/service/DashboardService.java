package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.model.Student;
import com.movinsync.shuttlemanagement.model.Trip;
import com.movinsync.shuttlemanagement.repository.StudentRepository;
import com.movinsync.shuttlemanagement.repository.TripRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DashboardService {

    private final StudentRepository studentRepository;
    private final TripRepository tripRepository;

    public DashboardService(StudentRepository studentRepository, TripRepository tripRepository) {
        this.studentRepository = studentRepository;
        this.tripRepository = tripRepository;
    }

    public List<Map<String, Object>> getStudentOverview() {
        List<Map<String, Object>> report = new ArrayList<>();
        List<Student> students = studentRepository.findAll();

        for (Student student : students) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("studentId", student.getId());
            entry.put("name", student.getName());
            entry.put("email", student.getEmail());
            entry.put("walletBalance", student.getWallet().getBalance());

            List<Trip> trips = tripRepository.findByStudentId(student.getId());
            int totalSpent = trips.stream().mapToInt(Trip::getFare).sum();
            entry.put("totalFareSpent", totalSpent);
            entry.put("tripCount", trips.size());

            report.add(entry);
        }

        return report;
    }
}
