package com.movinsync.shuttlemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FrequentRouteResult {

    private String fromStop;
    private String toStop;
    private long tripCount;
    private LocalDateTime lastBooked;
}
