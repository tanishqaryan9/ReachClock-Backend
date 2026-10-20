package com.routealarm.ReachClock.eta;

import java.util.List;
import java.util.UUID;

public interface RouteService {

    EtaResult calculateEta(UUID tripId, double fromLat, double fromLng,
                           double toLat, double toLng, Double speedMetersPerSecond);

    List<LatLng> route(double fromLat, double fromLng, double toLat, double toLng);

    void forget(UUID tripId);
}
