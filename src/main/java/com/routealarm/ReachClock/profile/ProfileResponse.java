package com.routealarm.ReachClock.profile;

import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String shareCode,
        String displayName
) {}
