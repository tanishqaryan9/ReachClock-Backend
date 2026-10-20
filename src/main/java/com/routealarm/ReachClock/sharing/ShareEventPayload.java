package com.routealarm.ReachClock.sharing;

import java.util.UUID;

public record ShareEventPayload(
        String event,
        UUID shareId,
        String ownerName,
        String destinationName,
        Integer minutes,
        Long remainingSeconds,
        long atEpochMs
) {}
