package com.routealarm.ReachClock.alarm;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AlarmServiceImpl implements AlarmService {

    private final AlarmRepository alarmRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CreateAlarmResponse> getAlarms(UUID userId) {
        return alarmRepository.findAllByUserId(userId).stream().map(CreateAlarmResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CreateAlarmResponse getAlarm(UUID id, UUID userId) {
        return CreateAlarmResponse.from(find(id, userId));
    }

    @Override
    public CreateAlarmResponse createAlarm(UUID userId, CreateAlarmRequest r) {
        validateByType(r.type(), r.thresholdMinutes(), r.radiusMeters());
        Alarm alarm = new Alarm();
        alarm.setUserId(userId);
        apply(alarm, r.type(), r.destinationName(), r.latitude(), r.longitude(),
                r.thresholdMinutes(), r.radiusMeters());
        if (r.status() != null) alarm.setStatus(r.status());
        return CreateAlarmResponse.from(alarmRepository.save(alarm));
    }

    @Override
    public CreateAlarmResponse updateAlarm(UUID id, UUID userId, UpdateAlarmRequest r) {
        validateByType(r.type(), r.thresholdMinutes(), r.radiusMeters());
        Alarm alarm = find(id, userId);
        apply(alarm, r.type(), r.destinationName(), r.latitude(), r.longitude(),
                r.thresholdMinutes(), r.radiusMeters());
        alarm.setStatus(r.status());
        return CreateAlarmResponse.from(alarmRepository.save(alarm));
    }

    @Override
    public void deleteAlarm(UUID id, UUID userId) {
        alarmRepository.delete(find(id, userId));
    }

    private Alarm find(UUID id, UUID userId) {
        return alarmRepository.findByIdAndUserId(id, userId).orElseThrow(AlarmNotFoundException::new);
    }

    // Adjust to your actual alarm semantics
    private void validateByType(AlarmType type, Integer threshold, Integer radius) {
        if (type == AlarmType.ETA_BEFORE && threshold == null)
            throw new IllegalArgumentException("thresholdMinutes is required for ETA_BEFORE");
        if (type != AlarmType.ETA_BEFORE && radius == null)
            throw new IllegalArgumentException("radiusMeters is required for " + type);
    }

    private void apply(Alarm a, AlarmType type, String name, double lat, double lng,
                       Integer threshold, Integer radius) {
        a.setType(type);
        a.setDestinationName(name);
        a.setLatitude(lat);
        a.setLongitude(lng);
        a.setThresholdMinutes(threshold);
        a.setRadiusMeters(radius);
    }
}