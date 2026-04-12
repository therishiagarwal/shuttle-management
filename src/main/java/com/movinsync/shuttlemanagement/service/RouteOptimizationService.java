package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.dto.RouteOptimizationResult;
import com.movinsync.shuttlemanagement.model.Route;
import com.movinsync.shuttlemanagement.model.Stop;
import com.movinsync.shuttlemanagement.repository.RouteRepository;
import com.movinsync.shuttlemanagement.repository.StopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Finds the optimal path between two stops across all routes using Dijkstra's algorithm.
 *
 * Graph model:
 *   - Nodes  : every Stop in the system
 *   - Edges  : consecutive stop pairs within a Route, weighted by Haversine distance
 *
 * Time  : O((V + E) log V)  — V = stops, E = route connections
 * Space : O(V + E)          — adjacency list + priority queue
 */
@Service
public class RouteOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(RouteOptimizationService.class);

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final FareCalculatorService fareCalculatorService;

    public RouteOptimizationService(RouteRepository routeRepository,
                                    StopRepository stopRepository,
                                    FareCalculatorService fareCalculatorService) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.fareCalculatorService = fareCalculatorService;
    }

    public RouteOptimizationResult findOptimalPath(Long fromStopId, Long toStopId) {
        Stop source = stopRepository.findById(fromStopId)
                .orElseThrow(() -> new RuntimeException("From Stop not found"));
        Stop destination = stopRepository.findById(toStopId)
                .orElseThrow(() -> new RuntimeException("To Stop not found"));

        if (source.getId().equals(destination.getId())) {
            throw new RuntimeException("Source and destination stops must be different");
        }

        // Build adjacency list: stopId -> list of (neighbourStop, distanceKm)
        Map<Long, List<double[]>> graph = buildGraph();

        // Dijkstra
        Map<Long, Double> dist     = new HashMap<>();
        Map<Long, Long>   previous = new HashMap<>();
        PriorityQueue<long[]> pq   = new PriorityQueue<>(Comparator.comparingDouble(a -> a[1]));

        dist.put(source.getId(), 0.0);
        pq.offer(new long[]{source.getId(), Double.doubleToLongBits(0.0)});

        while (!pq.isEmpty()) {
            long[] curr      = pq.poll();
            long   currId    = curr[0];
            double currDist  = Double.longBitsToDouble(curr[1]);

            if (currDist > dist.getOrDefault(currId, Double.MAX_VALUE)) continue;

            for (double[] edge : graph.getOrDefault(currId, Collections.emptyList())) {
                long   neighbourId  = (long) edge[0];
                double newDist      = currDist + edge[1];

                if (newDist < dist.getOrDefault(neighbourId, Double.MAX_VALUE)) {
                    dist.put(neighbourId, newDist);
                    previous.put(neighbourId, currId);
                    pq.offer(new long[]{neighbourId, Double.doubleToLongBits(newDist)});
                }
            }
        }

        if (!dist.containsKey(destination.getId())) {
            log.warn("No route found between stops: fromStopId={}, toStopId={}", fromStopId, toStopId);
            throw new RuntimeException("No route found between the selected stops");
        }

        // Reconstruct path
        List<Stop> path = reconstructPath(previous, source.getId(), destination.getId());

        double totalDistanceKm = dist.get(destination.getId());
        int    totalFare       = calculatePathFare(path);

        log.info("Optimal path found: fromStopId={}, toStopId={}, stops={}, distanceKm={}, fare={}",
                fromStopId, toStopId, path.size(), Math.round(totalDistanceKm * 100.0) / 100.0, totalFare);
        return new RouteOptimizationResult(path, totalFare, Math.round(totalDistanceKm * 100.0) / 100.0);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<Long, List<double[]>> buildGraph() {
        Map<Long, List<double[]>> graph = new HashMap<>();
        List<Route> routes = routeRepository.findAll();

        for (Route route : routes) {
            List<Stop> stops = route.getStops();
            for (int i = 0; i < stops.size() - 1; i++) {
                Stop a = stops.get(i);
                Stop b = stops.get(i + 1);
                double distance = haversine(a, b);

                graph.computeIfAbsent(a.getId(), k -> new ArrayList<>())
                        .add(new double[]{b.getId(), distance});
                // Bidirectional — shuttle can be boarded at either end
                graph.computeIfAbsent(b.getId(), k -> new ArrayList<>())
                        .add(new double[]{a.getId(), distance});
            }
        }
        return graph;
    }

    private List<Stop> reconstructPath(Map<Long, Long> previous, Long sourceId, Long destId) {
        LinkedList<Stop> path = new LinkedList<>();
        Long current = destId;

        while (current != null) {
            Stop stop = stopRepository.findById(current)
                    .orElseThrow(() -> new RuntimeException("Stop not found during path reconstruction"));
            path.addFirst(stop);
            current = previous.get(current);
        }

        if (!path.getFirst().getId().equals(sourceId)) {
            throw new RuntimeException("No route found between the selected stops");
        }
        return path;
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
