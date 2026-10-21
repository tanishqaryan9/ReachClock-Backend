package com.routealarm.ReachClock.eta;

import java.util.List;

public record RouteDetail(
        List<LatLng> points,
        List<RouteSegmentDto> segments,
        double distanceMeters,
        long durationSeconds,
        int delaySeconds,
        int delayMinutes,
        String trafficCondition
) {}
