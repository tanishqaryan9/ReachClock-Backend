package com.routealarm.ReachClock.alarm;

import jakarta.validation.constraints.*;

public record UpdateAlarmRequest(
        @NotNull AlarmType type,

        @NotNull AlarmStatus status,

        @Size(max = 200)
        String destinationName,

        @NotNull
        @DecimalMax("90.0")
        @DecimalMin("-90.0")
        Double latitude,

        @NotNull
        @DecimalMax("180.0")
        @DecimalMin("-180.0")
        Double longitude,

        @Min(1)
        @Max(120)
        Integer thresholdMinutes,

        @Min(50)
        @Max(5000)
        Integer radiusMeters
) { }