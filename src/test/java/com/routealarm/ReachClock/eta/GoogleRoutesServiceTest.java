package com.routealarm.ReachClock.eta;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GoogleRoutesServiceTest {

    @Test
    void testDecodePolyline5() {
        // Standard polyline encoding example from Google documentation:
        // "_p~iF~ps|U_ulLnnqC_mqNvxq`@" ->
        // (38.5, -120.2), (40.7, -120.95), (43.252, -126.453)
        String encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";
        List<LatLng> points = GoogleRoutesService.decodePolyline5(encoded);

        assertEquals(3, points.size());
        assertEquals(38.5, points.get(0).latitude(), 1e-4);
        assertEquals(-120.2, points.get(0).longitude(), 1e-4);
        assertEquals(40.7, points.get(1).latitude(), 1e-4);
        assertEquals(-120.95, points.get(1).longitude(), 1e-4);
        assertEquals(43.252, points.get(2).latitude(), 1e-4);
        assertEquals(-126.453, points.get(2).longitude(), 1e-4);
    }

    @Test
    void testParseDurationSeconds() {
        assertEquals(4212L, GoogleRoutesService.parseDurationSeconds("4212s"));
        assertEquals(3436L, GoogleRoutesService.parseDurationSeconds("3436s"));
        assertEquals(120L, GoogleRoutesService.parseDurationSeconds("120.5s"));
        assertEquals(0L, GoogleRoutesService.parseDurationSeconds(null));
        assertEquals(0L, GoogleRoutesService.parseDurationSeconds(""));
    }
}
