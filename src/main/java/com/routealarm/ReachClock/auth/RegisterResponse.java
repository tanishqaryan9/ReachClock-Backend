package com.routealarm.ReachClock.auth;

import java.util.UUID;

public record RegisterResponse(UUID userId, String token) {}
