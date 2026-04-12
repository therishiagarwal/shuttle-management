package com.movinsync.shuttlemanagement.controller;

import com.movinsync.shuttlemanagement.dto.BookingResult;
import com.movinsync.shuttlemanagement.dto.FrequentRouteResult;
import com.movinsync.shuttlemanagement.model.Trip;
import com.movinsync.shuttlemanagement.service.TripService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping("/book")
    public BookingResult bookTrip(@RequestParam Long studentId,
                                  @RequestParam Long fromStopId,
                                  @RequestParam Long toStopId) {
        return tripService.bookTrip(studentId, fromStopId, toStopId);
    }

    @DeleteMapping("/{tripId}/cancel")
    public Trip cancelTrip(@PathVariable Long tripId,
                           @RequestParam Long studentId) {
        return tripService.cancelTrip(tripId, studentId);
    }

    @GetMapping("/student/{studentId}")
    public List<Trip> getStudentTrips(@PathVariable Long studentId) {
        return tripService.getTripsForStudent(studentId);
    }

    @GetMapping("/student/{studentId}/total-fare")
    public int getTotalFare(@PathVariable Long studentId) {
        return tripService.getTotalFareSpentByStudent(studentId);
    }

    @GetMapping("/student/{studentId}/frequent-routes")
    public List<FrequentRouteResult> getFrequentRoutes(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "3") int limit) {
        return tripService.getFrequentRoutes(studentId, limit);
    }
}
