package com.routealarm.ReachClock.websocket;

import com.routealarm.ReachClock.alarm.AlarmType;

import java.util.UUID;

/** Pushed to /user/queue/alarm-events, e.g. event = "ALARM_TRIGGERED". */
public record AlarmEvent(
        String event,
        UUID alarmId,
        UUID tripId,
        AlarmType alarmType,
        String destinationName,
        long remainingSeconds,
        double distanceMeters,
        long triggeredAtEpochMs
) {}
