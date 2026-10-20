package com.routealarm.ReachClock.sharing;

import java.util.List;
import java.util.UUID;

public record ShareResponse(
        UUID id,
        UUID tripId,
        String recipientName,
        String recipientCode,
        List<Integer> etaMinutes,
        boolean notifyOnArrival,
        boolean notifyOnLeave,
        String status
) {}
