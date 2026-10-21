package com.routealarm.ReachClock.trip;

import com.routealarm.ReachClock.eta.RouteDetail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<TripResponse> start(@RequestAttribute("userId") UUID userId,
                                              @Valid @RequestBody StartTripRequest request) {
        TripResponse trip = tripService.start(userId, request.alarmId());
        return ResponseEntity.created(URI.create("/api/v1/trips/" + trip.id())).body(trip);
    }

    @GetMapping("/active")
    public List<TripResponse> active(@RequestAttribute("userId") UUID userId) {
        return tripService.active(userId);
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID tripId,
                                       @RequestAttribute("userId") UUID userId) {
        tripService.cancel(userId, tripId);
        return ResponseEntity.noContent().build();
    }

    /** Turn-by-turn road polyline with real-time multi-segment traffic details. */
    @GetMapping("/{tripId}/route")
    public RouteDetail route(@PathVariable UUID tripId,
                             @RequestAttribute("userId") UUID userId,
                             @RequestParam double lat,
                             @RequestParam double lng) {
        return tripService.detailedRoute(userId, tripId, lat, lng);
    }
}
