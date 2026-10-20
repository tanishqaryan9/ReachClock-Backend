package com.routealarm.ReachClock.location;

import org.springframework.stereotype.Component;

@Component
public class LocationValidator {

    public boolean isValid(LocationUpdate update) {
        return update != null
                && update.tripId() != null
                && update.latitude() >= -90
                && update.latitude() <= 90
                && update.longitude() >= -180
                && update.longitude() <= 180
                && Double.isFinite(update.accuracyMeters())
                && update.accuracyMeters() >= 0
                && update.accuracyMeters() <= 100;
    }
}
