package com.routealarm.ReachClock.eta;

import java.time.Instant;

public record EtaResult(
        long remainingSeconds,
        double distanceMeters,
        Instant calculatedAt,
        String trafficCondition,
        int delayMinutes
) {
    public EtaResult(long remainingSeconds, double distanceMeters, Instant calculatedAt) {
        this(remainingSeconds, distanceMeters, calculatedAt, "SMOOTH", 0);
    }
}
