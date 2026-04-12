package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.model.Stop;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

/**
 * Calculates fare using Haversine distance with optional peak-hour surcharge.
 *
 * Base fare : ceil(distanceKm * ratePerKm), minimum 1
 * Peak fare : ceil(baseFare * peakMultiplier)
 *
 * Peak windows (configurable):
 *   Morning : 07:00 – 09:00
 *   Evening : 17:00 – 19:00
 */
@Service
public class FareCalculatorService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final int    MINIMUM_FARE    = 1;

    @Value("${fare.rate-per-km:2}")
    private int ratePerKm;

    @Value("${fare.peak.morning-start:7}")
    private int morningStart;

    @Value("${fare.peak.morning-end:9}")
    private int morningEnd;

    @Value("${fare.peak.evening-start:17}")
    private int eveningStart;

    @Value("${fare.peak.evening-end:19}")
    private int eveningEnd;

    @Value("${fare.peak.multiplier:1.5}")
    private double peakMultiplier;

    public int calculate(Stop from, Stop to) {
        return calculate(from, to, LocalTime.now());
    }

    /**
     * Overload accepting an explicit time — used internally and in tests.
     */
    public int calculate(Stop from, Stop to, LocalTime at) {
        double distanceKm = haversineDistance(
                from.getLatitude(), from.getLongitude(),
                to.getLatitude(),   to.getLongitude()
        );
        int baseFare = (int) Math.ceil(distanceKm * ratePerKm);
        baseFare = Math.max(baseFare, MINIMUM_FARE);

        if (isPeakHour(at)) {
            return (int) Math.ceil(baseFare * peakMultiplier);
        }
        return baseFare;
    }

    public boolean isPeakHour(LocalTime time) {
        int hour = time.getHour();
        boolean morningPeak = hour >= morningStart && hour < morningEnd;
        boolean eveningPeak = hour >= eveningStart && hour < eveningEnd;
        return morningPeak || eveningPeak;
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
