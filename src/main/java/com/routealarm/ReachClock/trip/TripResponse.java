package com.routealarm.ReachClock.trip;

import java.time.Instant;
import java.util.UUID;

public record TripResponse(
        UUID id, UUID alarmId,
        double destinationLatitude, double destinationLongitude,
        TripStatus status, Instant startedAt, Instant endedAt) {

    public static TripResponse from(Trip t) {
        return new TripResponse(t.getId(), t.getAlarmId(),
                t.getDestinationLatitude(), t.getDestinationLongitude(),
                t.getStatus(), t.getStartedAt(), t.getEndedAt());
    }
}
