package com.routealarm.ReachClock.alarm;

import java.util.List;
import java.util.UUID;

// AlarmService.java
public interface AlarmService {
    List<CreateAlarmResponse> getAlarms(UUID userId);

    CreateAlarmResponse getAlarm(UUID id, UUID userId);

    CreateAlarmResponse createAlarm(UUID userId, CreateAlarmRequest request);

    CreateAlarmResponse updateAlarm(UUID id, UUID userId, UpdateAlarmRequest request);

    void deleteAlarm(UUID id, UUID userId);
}