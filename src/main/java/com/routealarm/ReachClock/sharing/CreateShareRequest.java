package com.routealarm.ReachClock.sharing;

import java.util.List;

public record CreateShareRequest(
        String recipientCode,
        List<Integer> etaMinutes,
        boolean notifyOnArrival,
        boolean notifyOnLeave
) {}
