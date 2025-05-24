package com.movinsync.shuttlemanagement.controller;

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
    public Trip bookTrip(@RequestParam Long studentId,
                         @RequestParam Long fromStopId,
                         @RequestParam Long toStopId,
                         @RequestParam int fare) {
        return tripService.bookTrip(studentId, fromStopId, toStopId, fare);
    }

    @GetMapping("/student/{studentId}")
    public List<Trip> getStudentTrips(@PathVariable Long studentId) {
        return tripService.getTripsForStudent(studentId);
    }
    
    @GetMapping("/student/{studentId}/total-fare")
public int getTotalFare(@PathVariable Long studentId) {
    return tripService.getTotalFareSpentByStudent(studentId);
}

}
