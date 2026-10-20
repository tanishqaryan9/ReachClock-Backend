package com.routealarm.ReachClock.eta;

import java.time.Instant;

public record EtaResult(
        long remainingSeconds,
        double distanceMeters,
        Instant calculatedAt
) {}
