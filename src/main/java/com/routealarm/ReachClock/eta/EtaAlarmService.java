package com.routealarm.ReachClock.eta;

import com.routealarm.ReachClock.alarm.Alarm;
import com.routealarm.ReachClock.geofence.GeofenceCalculator;
import com.routealarm.ReachClock.location.LocationUpdate;
import com.routealarm.ReachClock.trip.Trip;
import com.routealarm.ReachClock.trip.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EtaAlarmService {

    private static final int DEFAULT_RADIUS_METERS = 200;
    private static final int DEFAULT_THRESHOLD_MINUTES = 5;

    private final GeofenceCalculator geofence;
    private final TripRepository tripRepository;

    public boolean shouldTrigger(Trip trip, Alarm alarm, LocationUpdate loc, EtaResult eta) {
        double distance = geofence.distanceMeters(
                loc.latitude(), loc.longitude(),
                trip.getDestinationLatitude(), trip.getDestinationLongitude());
        int radius = alarm.getRadiusMeters() != null ? alarm.getRadiusMeters() : DEFAULT_RADIUS_METERS;
        int threshold = alarm.getThresholdMinutes() != null ? alarm.getThresholdMinutes() : DEFAULT_THRESHOLD_MINUTES;

        return switch (alarm.getType()) {
            // ring when within the radius of the destination
            case ARRIVAL -> distance <= radius;
            // ring N minutes before arrival (distance check is a safety net if the ETA is off)
            case ETA_BEFORE -> eta.remainingSeconds() <= threshold * 60L || distance <= radius;
            // armed once we have been inside the radius, rings when we leave it
            case DEPARTURE -> {
                if (distance <= radius) {
                    if (!trip.isInsideSeen()) {
                        trip.setInsideSeen(true);
                        tripRepository.save(trip);
                    }
                    yield false;
                }
                yield trip.isInsideSeen();
            }
        };
    }
}
