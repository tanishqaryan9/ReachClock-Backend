package com.routealarm.ReachClock.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ProfileResponse getProfile(@RequestAttribute("userId") UUID userId) {
        return profileService.getProfile(userId);
    }

    @PutMapping
    public ProfileResponse updateProfile(@RequestAttribute("userId") UUID userId,
                                         @RequestBody UpdateProfileRequest request) {
        return profileService.updateDisplayName(userId, request);
    }

    @PutMapping("/push-token")
    public ResponseEntity<Void> updatePushToken(@RequestAttribute("userId") UUID userId,
                                                @RequestBody PushTokenRequest request) {
        profileService.updatePushToken(userId, request.token());
        return ResponseEntity.noContent().build();
    }
}
