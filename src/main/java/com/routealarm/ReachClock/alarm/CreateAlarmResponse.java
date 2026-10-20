// CreateAlarmResponse.java
package com.routealarm.ReachClock.alarm;

import java.time.Instant;
import java.util.UUID;

public record CreateAlarmResponse(
        UUID id,
        UUID userId,
        AlarmType type,
        AlarmStatus status,
        String destinationName,
        Double latitude,
        Double longitude,
        Integer thresholdMinutes,
        Integer radiusMeters,
        Instant createdAt
) {
    public static CreateAlarmResponse from(Alarm a) {
        return new CreateAlarmResponse(a.getId(), a.getUserId(), a.getType(), a.getStatus(),
                a.getDestinationName(), a.getLatitude(), a.getLongitude(),
                a.getThresholdMinutes(), a.getRadiusMeters(), a.getCreatedAt());
    }
}