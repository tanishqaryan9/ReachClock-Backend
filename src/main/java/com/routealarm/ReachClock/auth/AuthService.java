package com.routealarm.ReachClock.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SHARE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int SHARE_CODE_LENGTH = 6;

    private final DeviceRepository devices;

    public RegisterResponse register() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        Device device = new Device();
        device.setTokenHash(hash(token));
        device.setShareCode(generateUniqueShareCode());
        device = devices.save(device);
        return new RegisterResponse(device.getId(), token);
    }

    public Optional<UUID> authenticate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return devices.findByTokenHash(hash(token)).map(Device::getId);
    }

    private String generateUniqueShareCode() {
        String code;
        do {
            code = generateShareCode();
        } while (devices.existsByShareCode(code));
        return code;
    }

    private static String generateShareCode() {
        StringBuilder sb = new StringBuilder(SHARE_CODE_LENGTH);
        for (int i = 0; i < SHARE_CODE_LENGTH; i++) {
            sb.append(SHARE_CODE_CHARS.charAt(RANDOM.nextInt(SHARE_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
