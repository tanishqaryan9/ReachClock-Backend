package com.routealarm.ReachClock.sharing;

import java.util.UUID;

public record ShareUpdatePayload(
        UUID shareId,
        double latitude,
        double longitude,
        long remainingSeconds,
        double distanceMeters,
        long calculatedAtEpochMs,
        String status
) {}
