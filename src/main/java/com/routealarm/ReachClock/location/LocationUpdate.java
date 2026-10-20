package com.routealarm.ReachClock.location;

import java.time.Instant;
import java.util.UUID;

public record LocationUpdate(
        UUID tripId,
        double latitude,
        double longitude,
        double accuracyMeters,
        Double speedMetersPerSecond,
        Instant recordedAt
) {}
