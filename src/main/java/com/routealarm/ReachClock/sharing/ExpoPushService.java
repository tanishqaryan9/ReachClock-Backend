package com.routealarm.ReachClock.sharing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpoPushService {

    private final org.springframework.web.client.RestTemplate restTemplate;

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    public void send(String pushToken, String title, String body, Object data) {
        if (pushToken == null || pushToken.isBlank()) return;
        try {
            Map<String, Object> payload = Map.of(
                    "to", pushToken,
                    "title", title,
                    "body", body,
                    "channelId", "share",
                    "sound", "default",
                    "priority", "high",
                    "data", data != null ? data : Map.of()
            );
            restTemplate.postForEntity(EXPO_PUSH_URL, payload, String.class);
        } catch (Exception e) {
            log.warn("Failed to send Expo push notification to {}: {}", pushToken, e.getMessage());
        }
    }
}
