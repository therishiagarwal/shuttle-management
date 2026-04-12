package com.movinsync.shuttlemanagement.controller;

import com.movinsync.shuttlemanagement.dto.RouteOptimizationResult;
import com.movinsync.shuttlemanagement.model.Route;
import com.movinsync.shuttlemanagement.service.RouteOptimizationService;
import com.movinsync.shuttlemanagement.service.RouteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routeService;
    private final RouteOptimizationService optimizationService;

    public RouteController(RouteService routeService,
                           RouteOptimizationService optimizationService) {
        this.routeService = routeService;
        this.optimizationService = optimizationService;
    }

    @PostMapping
    public Route createRoute(@Valid @RequestBody Route route) {
        return routeService.saveRoute(route);
    }

    @GetMapping
    public List<Route> getAllRoutes() {
        return routeService.getAllRoutes();
    }

    @PutMapping("/{id}")
    public Route updateRoute(@PathVariable Long id, @Valid @RequestBody Route route) {
        return routeService.updateRoute(id, route);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoute(@PathVariable Long id) {
        routeService.deleteRoute(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{routeId}/stops/{stopId}")
    public Route addStop(@PathVariable Long routeId, @PathVariable Long stopId) {
        return routeService.addStopToRoute(routeId, stopId);
    }

    @DeleteMapping("/{routeId}/stops/{stopId}")
    public Route removeStop(@PathVariable Long routeId, @PathVariable Long stopId) {
        return routeService.removeStopFromRoute(routeId, stopId);
    }

    @GetMapping("/optimize")
    public RouteOptimizationResult optimizeRoute(@RequestParam Long fromStopId,
                                                  @RequestParam Long toStopId) {
        return optimizationService.findOptimalPath(fromStopId, toStopId);
    }
}
