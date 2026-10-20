package com.routealarm.ReachClock.alarm;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

// AlarmController.java
@RestController
@RequestMapping("/api/v1/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;

    @GetMapping
    public List<CreateAlarmResponse> getAlarms(@RequestAttribute("userId") UUID userId) {
        return alarmService.getAlarms(userId);
    }

    @GetMapping("/{alarmId}")
    public CreateAlarmResponse getAlarm(@PathVariable UUID alarmId,
                                        @RequestAttribute("userId") UUID userId) {
        return alarmService.getAlarm(alarmId, userId);
    }

    @PostMapping
    public ResponseEntity<CreateAlarmResponse> createAlarm(@RequestAttribute("userId") UUID userId,
                                                           @Valid @RequestBody CreateAlarmRequest request) {
        CreateAlarmResponse created = alarmService.createAlarm(userId, request);
        return ResponseEntity.created(URI.create("/api/v1/alarms/" + created.id())).body(created);
    }

    @PutMapping("/{alarmId}")
    public CreateAlarmResponse updateAlarm(@PathVariable UUID alarmId,
                                           @RequestAttribute("userId") UUID userId,
                                           @Valid @RequestBody UpdateAlarmRequest request) {
        return alarmService.updateAlarm(alarmId, userId, request);
    }

    @DeleteMapping("/{alarmId}")
    public ResponseEntity<Void> deleteAlarm(@PathVariable UUID alarmId,
                                            @RequestAttribute("userId") UUID userId) {
        alarmService.deleteAlarm(alarmId, userId);
        return ResponseEntity.noContent().build();
    }
}
