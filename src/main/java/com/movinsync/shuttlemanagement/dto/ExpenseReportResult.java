package com.movinsync.shuttlemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ExpenseReportResult {

    private String period;          // e.g. "2026-04" or "2026-04-06 to 2026-04-12"
    private int totalFareSpent;
    private long tripCount;
    private int walletBalance;
    private List<TripSummary> trips;

    @Data
    @AllArgsConstructor
    public static class TripSummary {
        private String date;
        private String fromStop;
        private String toStop;
        private int fare;
        private String status;
    }
}
