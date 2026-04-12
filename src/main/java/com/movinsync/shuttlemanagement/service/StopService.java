package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.dto.NearestStopResult;
import com.movinsync.shuttlemanagement.model.Stop;
import com.movinsync.shuttlemanagement.repository.RouteRepository;
import com.movinsync.shuttlemanagement.repository.StopRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

/**
 * Stop management service.
 *
 * Nearest-stop search uses the Haversine formula to compute great-circle
 * distance from the given coordinates to every stop, then returns the
 * closest N sorted ascending by distance.
 *
 * Time  : O(n)  — single pass over all stops
 * Space : O(n)  — result list
 */
@Service
public class StopService {

    private static final double EARTH_RADIUS_METRES = 6_371_000.0;

    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;

    public StopService(StopRepository stopRepository, RouteRepository routeRepository) {
        this.stopRepository = stopRepository;
        this.routeRepository = routeRepository;
    }

    @CacheEvict(value = "stops", allEntries = true)
    public Stop saveStop(Stop stop) {
        return stopRepository.save(stop);
    }

    @Cacheable("stops")
    public List<Stop> getAllStops() {
        return stopRepository.findAll();
    }

    @CacheEvict(value = "stops", allEntries = true)
    public Stop updateStop(Long id, Stop updated) {
        Stop existing = stopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stop not found"));
        existing.setName(updated.getName());
        existing.setLatitude(updated.getLatitude());
        existing.setLongitude(updated.getLongitude());
        return stopRepository.save(existing);
    }

    @CacheEvict(value = "stops", allEntries = true)
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

    /**
     * Returns the {@code limit} nearest stops to the given coordinates,
     * sorted by ascending Haversine distance in metres.
     */
    public List<NearestStopResult> findNearestStops(double lat, double lng, int limit) {
        if (limit <= 0) throw new RuntimeException("Limit must be greater than 0");

        return stopRepository.findAll()
                .stream()
                .map(stop -> new NearestStopResult(stop, haversineMetres(lat, lng,
                        stop.getLatitude(), stop.getLongitude())))
                .sorted(Comparator.comparingDouble(NearestStopResult::getDistanceMetres))
                .limit(limit)
                .toList();
    }

    private double haversineMetres(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double dist = EARTH_RADIUS_METRES * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(dist * 100.0) / 100.0;
    }
}
