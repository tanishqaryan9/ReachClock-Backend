package com.routealarm.ReachClock.eta;

import com.routealarm.ReachClock.geofence.GeofenceCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OsrmRouteServiceTest {

    @Test
    void testRoutePoints() {
        OsrmRouteService service = new OsrmRouteService(new GeofenceCalculator(), "https://router.project-osrm.org");
        List<LatLng> points = service.route(28.6139, 77.2090, 28.7041, 77.1025);
        System.out.println("Points returned: " + points.size());
        assertTrue(points.size() > 50, "Expected full road polyline (>50 points), but got: " + points.size());
    }

    @Test
    void testDetailedRoute() {
        OsrmRouteService service = new OsrmRouteService(new GeofenceCalculator(), "https://router.project-osrm.org");
        RouteDetail detail = service.detailedRoute(28.6139, 77.2090, 28.7041, 77.1025);
        assertNotNull(detail);
        assertTrue(detail.points().size() > 50);
        assertNotNull(detail.segments());
        assertFalse(detail.segments().isEmpty());
        System.out.println("Segments returned: " + detail.segments().size());
        System.out.println("Traffic condition: " + detail.trafficCondition());
        System.out.println("Delay seconds: " + detail.delaySeconds());
    }
}
