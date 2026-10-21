package com.routealarm.ReachClock.eta;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    /** Turn-by-turn road polyline with real-time multi-segment traffic details. */
    @GetMapping
    public RouteDetail route(@RequestParam double fromLat,
                             @RequestParam double fromLng,
                             @RequestParam double toLat,
                             @RequestParam double toLng) {
        return routeService.detailedRoute(fromLat, fromLng, toLat, toLng);
    }
}
