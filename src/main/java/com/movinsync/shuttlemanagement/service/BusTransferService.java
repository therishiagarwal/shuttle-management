package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.dto.TransferResult;
import com.movinsync.shuttlemanagement.model.Route;
import com.movinsync.shuttlemanagement.model.Stop;
import com.movinsync.shuttlemanagement.repository.RouteRepository;
import com.movinsync.shuttlemanagement.repository.StopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Finds the best two-leg transfer journey between two stops.
 *
 * Strategy:
 *   1. Find all routes containing fromStop  (leg-1 candidates)
 *   2. Find all routes containing toStop    (leg-2 candidates)
 *   3. For every (leg1Route, leg2Route) pair find shared stops — these are transfer points
 *   4. Pick the transfer stop that minimises total Haversine distance
 *
 * Time  : O(R² × S)  — R = number of routes, S = stops per route
 * Space : O(S)       — candidate transfer stops
 */
@Service
public class BusTransferService {

    private static final Logger log = LoggerFactory.getLogger(BusTransferService.class);

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final FareCalculatorService fareCalculatorService;

    public BusTransferService(RouteRepository routeRepository,
                               StopRepository stopRepository,
                               FareCalculatorService fareCalculatorService) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.fareCalculatorService = fareCalculatorService;
    }

    public TransferResult findBestTransfer(Long fromStopId, Long toStopId) {
        Stop from = stopRepository.findById(fromStopId)
                .orElseThrow(() -> new RuntimeException("From Stop not found"));
        Stop to = stopRepository.findById(toStopId)
                .orElseThrow(() -> new RuntimeException("To Stop not found"));

        List<Route> allRoutes = routeRepository.findAll();

        // Routes that contain the fromStop
        List<Route> leg1Routes = allRoutes.stream()
                .filter(r -> containsStop(r, fromStopId))
                .toList();

        // Routes that contain the toStop
        List<Route> leg2Routes = allRoutes.stream()
                .filter(r -> containsStop(r, toStopId))
                .toList();

        if (leg1Routes.isEmpty()) {
            log.warn("No routes from source stop: fromStopId={}", fromStopId);
            throw new RuntimeException("No routes found from the source stop");
        }
        if (leg2Routes.isEmpty()) {
            log.warn("No routes to destination stop: toStopId={}", toStopId);
            throw new RuntimeException("No routes found to the destination stop");
        }

        // Find best transfer combination
        double bestDist = Double.MAX_VALUE;
        Stop bestTransferStop = null;
        Route bestLeg1Route = null;
        Route bestLeg2Route = null;

        for (Route r1 : leg1Routes) {
            for (Route r2 : leg2Routes) {
                if (r1.getId().equals(r2.getId())) continue; // same route = no transfer needed

                // Find shared stops between r1 and r2
                Set<Long> r1StopIds = stopIds(r1);
                for (Stop candidate : r2.getStops()) {
                    if (!r1StopIds.contains(candidate.getId())) continue;
                    if (candidate.getId().equals(fromStopId)) continue;
                    if (candidate.getId().equals(toStopId)) continue;

                    double dist = haversine(from, candidate) + haversine(candidate, to);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestTransferStop = candidate;
                        bestLeg1Route = r1;
                        bestLeg2Route = r2;
                    }
                }
            }
        }

        if (bestTransferStop == null) {
            log.warn("No transfer route found: fromStopId={}, toStopId={}", fromStopId, toStopId);
            throw new RuntimeException("No transfer route found between the selected stops");
        }

        // Build full ordered path: leg1 stops + leg2 stops (excluding duplicate transfer stop)
        List<Stop> leg1Path = stopsOnRouteBetween(bestLeg1Route, from, bestTransferStop);
        List<Stop> leg2Path = stopsOnRouteBetween(bestLeg2Route, bestTransferStop, to);

        List<Stop> fullPath = new ArrayList<>(leg1Path);
        fullPath.addAll(leg2Path.subList(1, leg2Path.size())); // skip duplicate transfer stop

        int totalFare = calculatePathFare(fullPath);
        double totalDistKm = Math.round(bestDist * 100.0) / 100.0;

        log.info("Transfer route found: fromStopId={}, toStopId={}, transferStop={}, leg1={}, leg2={}, fare={}",
                fromStopId, toStopId, bestTransferStop.getName(),
                bestLeg1Route.getRouteName(), bestLeg2Route.getRouteName(), totalFare);
        return new TransferResult(
                fullPath,
                bestTransferStop,
                bestLeg1Route.getRouteName(),
                bestLeg2Route.getRouteName(),
                totalFare,
                totalDistKm
        );
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean containsStop(Route route, Long stopId) {
        return route.getStops().stream().anyMatch(s -> s.getId().equals(stopId));
    }

    private Set<Long> stopIds(Route route) {
        Set<Long> ids = new HashSet<>();
        for (Stop s : route.getStops()) ids.add(s.getId());
        return ids;
    }

    /**
     * Returns the ordered sub-list of stops on a route between two stops (inclusive).
     * Tries forward direction first, then reverse.
     */
    private List<Stop> stopsOnRouteBetween(Route route, Stop from, Stop to) {
        List<Stop> stops = route.getStops();
        int fromIdx = -1, toIdx = -1;

        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getId().equals(from.getId())) fromIdx = i;
            if (stops.get(i).getId().equals(to.getId()))   toIdx   = i;
        }

        if (fromIdx == -1 || toIdx == -1) return List.of(from, to);

        if (fromIdx <= toIdx) {
            return new ArrayList<>(stops.subList(fromIdx, toIdx + 1));
        } else {
            // Reverse direction
            List<Stop> reversed = new ArrayList<>(stops.subList(toIdx, fromIdx + 1));
            Collections.reverse(reversed);
            return reversed;
        }
    }

    private int calculatePathFare(List<Stop> path) {
        int total = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            total += fareCalculatorService.calculate(path.get(i), path.get(i + 1));
        }
        return total;
    }

    private double haversine(Stop a, Stop b) {
        final double R = 6371.0;
        double dLat = Math.toRadians(b.getLatitude()  - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.getLatitude()))
                * Math.cos(Math.toRadians(b.getLatitude()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
