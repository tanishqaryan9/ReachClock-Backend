package com.routealarm.ReachClock.eta;

import java.util.List;

public record RouteSegmentDto(
        List<LatLng> points,
        String condition, // "SMOOTH", "MODERATE", "JAM"
        double speedKmh,
        int delaySeconds
) {}
