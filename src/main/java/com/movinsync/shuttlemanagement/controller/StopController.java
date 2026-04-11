package com.movinsync.shuttlemanagement.controller;

import com.movinsync.shuttlemanagement.model.Stop;
import com.movinsync.shuttlemanagement.service.StopService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
    public Stop createStop(@Valid @RequestBody Stop stop) {
        return stopService.saveStop(stop);
    }

    @GetMapping
    public List<Stop> getAllStops() {
        return stopService.getAllStops();
    }

    @PutMapping("/{id}")
    public Stop updateStop(@PathVariable Long id, @Valid @RequestBody Stop stop) {
        return stopService.updateStop(id, stop);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStop(@PathVariable Long id) {
        stopService.deleteStop(id);
        return ResponseEntity.noContent().build();
    }
}
