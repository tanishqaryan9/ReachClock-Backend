package com.routealarm.ReachClock.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AlarmEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendTripUpdate(String userId, TripUpdateEvent event) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/trip-updates", event);
    }

    public void sendAlarmEvent(String userId, AlarmEvent event) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/alarm-events", event);
    }
}
