package com.movinsync.shuttlemanagement.dto;

import com.movinsync.shuttlemanagement.model.Stop;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NearestStopResult {

    private Stop stop;
    private double distanceMetres;
}
