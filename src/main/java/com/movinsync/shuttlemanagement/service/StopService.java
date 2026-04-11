package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.model.Stop;
import com.movinsync.shuttlemanagement.repository.RouteRepository;
import com.movinsync.shuttlemanagement.repository.StopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class StopService {

    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;

    public StopService(StopRepository stopRepository, RouteRepository routeRepository) {
        this.stopRepository = stopRepository;
        this.routeRepository = routeRepository;
    }

    public Stop saveStop(Stop stop) {
        return stopRepository.save(stop);
    }

    public List<Stop> getAllStops() {
        return stopRepository.findAll();
    }

    public Stop updateStop(Long id, Stop updated) {
        Stop existing = stopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stop not found"));
        existing.setName(updated.getName());
        existing.setLatitude(updated.getLatitude());
        existing.setLongitude(updated.getLongitude());
        return stopRepository.save(existing);
    }

    public void deleteStop(Long id) {
        if (!stopRepository.existsById(id)) {
            throw new RuntimeException("Stop not found");
        }
        if (routeRepository.existsByStopsId(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stop is assigned to one or more routes and cannot be deleted"
            );
        }
        stopRepository.deleteById(id);
    }
}
