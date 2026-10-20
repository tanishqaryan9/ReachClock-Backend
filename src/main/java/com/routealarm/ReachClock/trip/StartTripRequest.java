package com.routealarm.ReachClock.trip;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartTripRequest(@NotNull UUID alarmId) {}
