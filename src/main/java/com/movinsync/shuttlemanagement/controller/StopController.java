package com.movinsync.shuttlemanagement.controller;

import com.movinsync.shuttlemanagement.model.Stop;
import com.movinsync.shuttlemanagement.service.StopService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stops")
public class StopController {

    private final StopService stopService;

    public StopController(StopService stopService) {
        this.stopService = stopService;
    }

    @PostMapping
    public Stop createStop(@RequestBody Stop stop) {
        return stopService.saveStop(stop);
    }

    @GetMapping
    public List<Stop> getAllStops() {
        return stopService.getAllStops();
    }
}
