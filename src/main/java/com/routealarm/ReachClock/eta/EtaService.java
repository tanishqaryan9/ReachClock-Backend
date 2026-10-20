package com.routealarm.ReachClock.eta;

import com.routealarm.ReachClock.location.LocationUpdate;
import com.routealarm.ReachClock.trip.Trip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EtaService {

    private final RouteService routeService;

    public EtaResult updateEta(Trip trip, LocationUpdate location) {
        return routeService.calculateEta(
                trip.getId(),
                location.latitude(), location.longitude(),
                trip.getDestinationLatitude(), trip.getDestinationLongitude(),
                location.speedMetersPerSecond());
    }
}
