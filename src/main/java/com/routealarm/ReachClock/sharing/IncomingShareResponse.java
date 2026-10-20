package com.routealarm.ReachClock.sharing;

import java.util.UUID;

public record IncomingShareResponse(
        UUID id,
        String ownerName,
        String destinationName,
        double destinationLatitude,
        double destinationLongitude,
        String status,
        Double lastLatitude,
        Double lastLongitude,
        Long remainingSeconds,
        Double distanceMeters,
        Long updatedAtEpochMs
) {}
