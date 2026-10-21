package com.routealarm.ReachClock.profile;

import com.routealarm.ReachClock.auth.Device;
import com.routealarm.ReachClock.auth.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final DeviceRepository deviceRepository;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        Device device = deviceRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Device not found"));
        return new ProfileResponse(device.getId(), device.getShareCode(), device.getDisplayName());
    }

    @Transactional
    public ProfileResponse updateDisplayName(UUID userId, UpdateProfileRequest request) {
        Device device = deviceRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Device not found"));
        device.setDisplayName(request.displayName());
        deviceRepository.save(device);
        return new ProfileResponse(device.getId(), device.getShareCode(), device.getDisplayName());
    }

    @Transactional
    public void updatePushToken(UUID userId, String token) {
        Device device = deviceRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Device not found"));
        device.setPushToken(token);
        deviceRepository.save(device);
    }
}
