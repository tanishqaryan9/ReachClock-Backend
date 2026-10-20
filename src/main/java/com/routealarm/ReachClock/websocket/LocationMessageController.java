package com.routealarm.ReachClock.websocket;

import com.routealarm.ReachClock.location.LocationService;
import com.routealarm.ReachClock.location.LocationUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class LocationMessageController {

    private final LocationService locationService;

    @MessageMapping("/location")
    public void receiveLocation(LocationUpdate update, Principal principal) {
        if (principal == null) return;
        locationService.process(UUID.fromString(principal.getName()), update);
    }
}
