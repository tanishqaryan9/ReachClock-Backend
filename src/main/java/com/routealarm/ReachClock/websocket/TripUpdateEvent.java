package com.routealarm.ReachClock.websocket;

import java.util.UUID;

/** Pushed to /user/queue/trip-updates after every accepted location update. */
public record TripUpdateEvent(
        UUID tripId,
        double latitude,
        double longitude,
        long remainingSeconds,
        double distanceMeters,
        long calculatedAtEpochMs
) {}
