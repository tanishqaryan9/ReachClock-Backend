package com.routealarm.ReachClock.sharing;

import java.util.UUID;

public record ShareUpdatePayload(
        UUID shareId,
        double latitude,
        double longitude,
        long remainingSeconds,
        double distanceMeters,
        long calculatedAtEpochMs,
        String status,
        String trafficCondition,
        int delayMinutes
) {
    public ShareUpdatePayload(UUID shareId, double latitude, double longitude, long remainingSeconds, double distanceMeters, long calculatedAtEpochMs, String status) {
        this(shareId, latitude, longitude, remainingSeconds, distanceMeters, calculatedAtEpochMs, status, "SMOOTH", 0);
    }
}
