package com.movinsync.shuttlemanagement.dto;

import com.movinsync.shuttlemanagement.model.Trip;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BookingResult {

    private Trip trip;
    private int  baseFare;
    private int  finalFare;
    private boolean peakHour;
}
