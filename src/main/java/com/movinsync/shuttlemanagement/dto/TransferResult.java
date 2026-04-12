package com.movinsync.shuttlemanagement.dto;

import com.movinsync.shuttlemanagement.model.Stop;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TransferResult {

    // Full ordered stop list across both legs
    private List<Stop> fullPath;

    // The stop where the passenger switches shuttle
    private Stop transferStop;

    // Route name for leg 1 (fromStop -> transferStop)
    private String leg1Route;

    // Route name for leg 2 (transferStop -> toStop)
    private String leg2Route;

    // Combined fare for the entire journey (single deduction)
    private int totalFare;

    private double totalDistanceKm;
}
