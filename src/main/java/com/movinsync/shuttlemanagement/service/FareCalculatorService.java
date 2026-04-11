package com.movinsync.shuttlemanagement.service;

import com.movinsync.shuttlemanagement.model.Stop;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FareCalculatorService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final int MINIMUM_FARE = 1;

    @Value("${fare.rate-per-km:2}")
    private int ratePerKm;

    /**
     * Calculates fare based on Haversine great-circle distance between two stops.
     * Formula: fare = max(MINIMUM_FARE, ceil(distanceKm * ratePerKm))
     */
    public int calculate(Stop from, Stop to) {
        double distanceKm = haversineDistance(
                from.getLatitude(), from.getLongitude(),
                to.getLatitude(),   to.getLongitude()
        );
        int fare = (int) Math.ceil(distanceKm * ratePerKm);
        return Math.max(fare, MINIMUM_FARE);
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
