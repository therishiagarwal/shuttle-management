package com.movinsync.shuttlemanagement.dto;

import com.movinsync.shuttlemanagement.model.Stop;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RouteOptimizationResult {

    private List<Stop> stops;
    private int totalFare;
    private double totalDistanceKm;
}
