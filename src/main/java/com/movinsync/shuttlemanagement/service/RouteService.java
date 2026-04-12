package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.model.Route;
import com.movinsync.shuttlemanagement.model.Stop;
import com.movinsync.shuttlemanagement.repository.RouteRepository;
import com.movinsync.shuttlemanagement.repository.StopRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;

    public RouteService(RouteRepository routeRepository, StopRepository stopRepository) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
    }

    @CacheEvict(value = "routes", allEntries = true)
    public Route saveRoute(Route route) {
        return routeRepository.save(route);
    }

    @Cacheable("routes")
    public List<Route> getAllRoutes() {
        return routeRepository.findAll();
    }

    @CacheEvict(value = "routes", allEntries = true)
    public Route updateRoute(Long id, Route updated) {
        Route existing = routeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Route not found"));
        existing.setRouteName(updated.getRouteName());
        existing.setStops(updated.getStops());
        return routeRepository.save(existing);
    }

    @CacheEvict(value = "routes", allEntries = true)
    public void deleteRoute(Long id) {
        if (!routeRepository.existsById(id)) {
            throw new RuntimeException("Route not found");
        }
        routeRepository.deleteById(id);
    }

    @CacheEvict(value = "routes", allEntries = true)
    public Route addStopToRoute(Long routeId, Long stopId) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route not found"));
        Stop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new RuntimeException("Stop not found"));

        if (route.getStops().contains(stop)) {
            throw new RuntimeException("Stop is already part of this route");
        }
        route.getStops().add(stop);
        return routeRepository.save(route);
    }

    @CacheEvict(value = "routes", allEntries = true)
    public Route removeStopFromRoute(Long routeId, Long stopId) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route not found"));
        Stop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new RuntimeException("Stop not found"));

        if (route.getStops().size() <= 1) {
            throw new RuntimeException("Route must have at least one stop");
        }
        route.getStops().remove(stop);
        return routeRepository.save(route);
    }
}
