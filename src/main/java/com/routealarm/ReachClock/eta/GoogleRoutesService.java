package com.routealarm.ReachClock.eta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.routealarm.ReachClock.geofence.GeofenceCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Primary routing service backed by Google Routes API v2 (computeRoutes).
 * Uses TRAFFIC_AWARE_OPTIMAL routing preference for live-traffic ETA that
 * closely matches Google Maps. Falls back to OsrmRouteService on any error.
 */
@Slf4j
@Primary
@Service
public class GoogleRoutesService implements RouteService {

    // -----------------------------------------------------------------------
    // Google Routes API constants
    // -----------------------------------------------------------------------
    private static final String ROUTES_URL =
            "https://routes.googleapis.com/directions/v2:computeRoutes";
    private static final String FIELD_MASK =
            "routes.duration,routes.distanceMeters,routes.staticDuration," +
            "routes.travelAdvisory.speedReadingIntervals," +
            "routes.polyline.encodedPolyline";

    // Snapshot refresh: re-query Google every 20 s while actively tracking
    private static final Duration REFRESH = Duration.ofSeconds(20);
    // After a Google API error, wait 30 s before retrying (avoid hammering quota)
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofSeconds(30);

    // -----------------------------------------------------------------------
    // Internal DTOs for Google Routes API JSON
    // -----------------------------------------------------------------------
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GRouteResponse(List<GRoute> routes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GRoute(
            String duration,
            String staticDuration,
            Integer distanceMeters,
            GPolyline polyline,
            GTravelAdvisory travelAdvisory
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GPolyline(String encodedPolyline) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GTravelAdvisory(List<GSpeedInterval> speedReadingIntervals) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GSpeedInterval(
            Integer startPolylinePointIndex,
            Integer endPolylinePointIndex,
            String speed  // "NORMAL", "SLOW", "TRAFFIC_JAM"
    ) {}

    // -----------------------------------------------------------------------
    // Cached snapshot (per active trip)
    // -----------------------------------------------------------------------
    private record Snapshot(
            List<LatLng> polyline,
            List<RouteSegmentDto> segments,
            double distanceMeters,
            long durationSeconds,     // traffic-aware total duration
            long staticDurationSeconds,
            int delaySeconds,
            String trafficCondition,
            Instant at
    ) {}

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------
    private final String apiKey;
    private final RestClient googleClient;
    private final GeofenceCalculator geo;
    private final OsrmRouteService osrmFallback;

    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> retryAt   = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public GoogleRoutesService(
            GeofenceCalculator geo,
            @Qualifier("osrmRouteService") OsrmRouteService osrmFallback,
            @Value("${reachclock.google.api-key:AIzaSyDNJ9x9tfho_6dYlHJsbHGlK-TR_SnAfVg}") String apiKey) {
        this.geo = geo;
        this.osrmFallback = osrmFallback;
        this.apiKey = apiKey;

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(8));

        this.googleClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("Accept-Encoding", "identity")
                .defaultHeader("X-Goog-Api-Key", apiKey)
                .defaultHeader("X-Goog-FieldMask", FIELD_MASK)
                .build();
    }

    // -----------------------------------------------------------------------
    // RouteService interface
    // -----------------------------------------------------------------------

    @Override
    public EtaResult calculateEta(UUID tripId, double fromLat, double fromLng,
                                  double toLat, double toLng, Double speedMetersPerSecond) {
        Snapshot s = freshSnapshot(tripId, fromLat, fromLng, toLat, toLng);
        if (s == null) {
            // Fallback to OSRM
            return osrmFallback.calculateEta(tripId, fromLat, fromLng, toLat, toLng, speedMetersPerSecond);
        }

        Instant now = Instant.now();
        long elapsed = Math.max(0, Duration.between(s.at(), now).toSeconds());
        long remainingSec = Math.max(0, s.durationSeconds() - elapsed);
        double distTraveled = (speedMetersPerSecond != null && speedMetersPerSecond > 0) ? speedMetersPerSecond * elapsed : 0;
        double remainingMeters = Math.max(0, s.distanceMeters() - distTraveled);

        return new EtaResult(
                remainingSec,
                remainingMeters,
                now,
                s.trafficCondition(),
                s.delaySeconds() / 60
        );
    }

    @Override
    public List<LatLng> route(double fromLat, double fromLng, double toLat, double toLng) {
        RouteDetail d = detailedRoute(fromLat, fromLng, toLat, toLng);
        if (d != null && !d.points().isEmpty()) return d.points();
        return osrmFallback.route(fromLat, fromLng, toLat, toLng);
    }

    @Override
    public RouteDetail detailedRoute(double fromLat, double fromLng, double toLat, double toLng) {
        GRoute g = fetchGoogle(fromLat, fromLng, toLat, toLng);
        if (g == null) {
            return osrmFallback.detailedRoute(fromLat, fromLng, toLat, toLng);
        }
        return buildRouteDetail(g);
    }

    @Override
    public void forget(UUID tripId) {
        snapshots.remove(tripId);
        retryAt.remove(tripId);
        osrmFallback.forget(tripId);
    }

    // -----------------------------------------------------------------------
    // Snapshot management
    // -----------------------------------------------------------------------

    private Snapshot freshSnapshot(UUID tripId, double fromLat, double fromLng,
                                   double toLat, double toLng) {
        Instant now = Instant.now();
        Snapshot cached = snapshots.get(tripId);
        boolean stale = cached == null || Duration.between(cached.at(), now).compareTo(REFRESH) >= 0;
        Instant blocked = retryAt.get(tripId);
        boolean isBlocked = blocked != null && now.isBefore(blocked);

        if (stale && !isBlocked) {
            GRoute g = fetchGoogle(fromLat, fromLng, toLat, toLng);
            if (g != null) {
                List<LatLng> poly = decodePolyline5(g.polyline() != null ? g.polyline().encodedPolyline() : "");
                RouteAnalysis analysis = buildSegments(poly, g);
                long dur = parseDurationSeconds(g.duration());
                long staticDur = parseDurationSeconds(g.staticDuration());
                int delaySec = (int) Math.max(0, dur - staticDur);
                double delayRatio = (dur > 0) ? (double) delaySec / dur : 0;
                String cond;
                if (delayRatio >= 0.30) cond = "HEAVY";
                else if (delayRatio >= 0.15) cond = "MODERATE";
                else cond = "SMOOTH";

                cached = new Snapshot(
                        poly,
                        analysis.segments(),
                        g.distanceMeters() != null ? g.distanceMeters() : 0,
                        dur,
                        staticDur,
                        delaySec,
                        cond,
                        now
                );
                snapshots.put(tripId, cached);
            } else {
                retryAt.put(tripId, now.plus(RETRY_AFTER_FAILURE));
            }
        }
        return cached;
    }

    // -----------------------------------------------------------------------
    // Google Routes API call
    // -----------------------------------------------------------------------

    private GRoute fetchGoogle(double fromLat, double fromLng, double toLat, double toLng) {
        try {
            var body = Map.of(
                    "origin", Map.of("location", Map.of("latLng",
                            Map.of("latitude", fromLat, "longitude", fromLng))),
                    "destination", Map.of("location", Map.of("latLng",
                            Map.of("latitude", toLat, "longitude", toLng))),
                    "travelMode", "DRIVE",
                    "routingPreference", "TRAFFIC_AWARE_OPTIMAL",
                    "extraComputations", List.of("TRAFFIC_ON_POLYLINE")
            );

            GRouteResponse resp = googleClient.post()
                    .uri(ROUTES_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GRouteResponse.class);

            if (resp == null || resp.routes() == null || resp.routes().isEmpty()) {
                log.warn("Google Routes API returned empty response");
                return null;
            }
            GRoute route = resp.routes().get(0);
            log.debug("Google Routes: dist={}m dur={} staticDur={}",
                    route.distanceMeters(), route.duration(), route.staticDuration());
            return route;
        } catch (Exception e) {
            log.warn("Google Routes API request failed: {}", e.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Build RouteDetail / segments from Google response
    // -----------------------------------------------------------------------

    private RouteDetail buildRouteDetail(GRoute g) {
        List<LatLng> poly = decodePolyline5(g.polyline() != null ? g.polyline().encodedPolyline() : "");
        RouteAnalysis analysis = buildSegments(poly, g);

        long dur = parseDurationSeconds(g.duration());
        long staticDur = parseDurationSeconds(g.staticDuration());
        int delaySec = (int) Math.max(0, dur - staticDur);
        double delayRatio = dur > 0 ? (double) delaySec / dur : 0;

        String cond;
        if (delayRatio >= 0.30) cond = "HEAVY";
        else if (delayRatio >= 0.15) cond = "MODERATE";
        else cond = "SMOOTH";

        return new RouteDetail(
                poly,
                analysis.segments(),
                g.distanceMeters() != null ? g.distanceMeters() : 0,
                dur,
                delaySec,
                delaySec / 60,
                cond
        );
    }

    private record RouteAnalysis(List<RouteSegmentDto> segments) {}

    private RouteAnalysis buildSegments(List<LatLng> poly, GRoute g) {
        if (poly.isEmpty()) return new RouteAnalysis(Collections.emptyList());

        List<GSpeedInterval> intervals =
                (g.travelAdvisory() != null && g.travelAdvisory().speedReadingIntervals() != null)
                ? g.travelAdvisory().speedReadingIntervals()
                : Collections.emptyList();

        if (intervals.isEmpty()) {
            // No traffic data — return single smooth segment
            return new RouteAnalysis(List.of(
                    new RouteSegmentDto(new ArrayList<>(poly), "SMOOTH", 45.0, 0)
            ));
        }

        List<RouteSegmentDto> segments = new ArrayList<>();
        int lastCovered = 0;
        int n = poly.size();

        for (GSpeedInterval interval : intervals) {
            int start = interval.startPolylinePointIndex() != null ? interval.startPolylinePointIndex() : lastCovered;
            int end   = interval.endPolylinePointIndex()   != null ? interval.endPolylinePointIndex()   : (n - 1);

            start = Math.max(0, Math.min(start, n - 1));
            end   = Math.max(start, Math.min(end, n - 1));

            // Gap fill before interval
            if (start > lastCovered) {
                List<LatLng> gap = new ArrayList<>(poly.subList(lastCovered, start + 1));
                if (gap.size() > 1) {
                    segments.add(new RouteSegmentDto(gap, "SMOOTH", 45.0, 0));
                }
            }

            List<LatLng> segPoints = new ArrayList<>(poly.subList(start, end + 1));
            if (segPoints.size() > 1) {
                String cond = mapSpeed(interval.speed());
                double speedKmh = speedKmhFromCondition(cond);
                int delaySec = "JAM".equals(cond) ? 90 : ("MODERATE".equals(cond) ? 45 : 0);
                segments.add(new RouteSegmentDto(segPoints, cond, speedKmh, delaySec));
            }

            lastCovered = Math.max(lastCovered, end);
        }

        if (lastCovered < n - 1) {
            List<LatLng> tail = new ArrayList<>(poly.subList(lastCovered, n));
            if (tail.size() > 1) {
                segments.add(new RouteSegmentDto(tail, "SMOOTH", 45.0, 0));
            }
        }

        if (segments.isEmpty()) {
            segments.add(new RouteSegmentDto(new ArrayList<>(poly), "SMOOTH", 45.0, 0));
        }

        return new RouteAnalysis(segments);
    }

    private static String mapSpeed(String googleSpeed) {
        if (googleSpeed == null) return "SMOOTH";
        return switch (googleSpeed) {
            case "TRAFFIC_JAM" -> "JAM";
            case "SLOW"        -> "MODERATE";
            default            -> "SMOOTH"; // "NORMAL"
        };
    }

    private static double speedKmhFromCondition(String cond) {
        return switch (cond) {
            case "JAM"      -> 10.0;
            case "MODERATE" -> 25.0;
            default         -> 55.0;
        };
    }

    // -----------------------------------------------------------------------
    // Polyline5 decoder (Google's standard encoded polyline format)
    // -----------------------------------------------------------------------

    /**
     * Decodes a Google Maps encoded polyline (Polyline 5 format) into a list of LatLng points.
     * See: https://developers.google.com/maps/documentation/utilities/polylinealgorithm
     */
    static List<LatLng> decodePolyline5(String encoded) {
        List<LatLng> points = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return points;

        int index = 0;
        int len   = encoded.length();
        int lat   = 0;
        int lng   = 0;

        while (index < len) {
            // Decode latitude delta
            int result = 0;
            int shift  = 0;
            int b;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dLat = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lat += dLat;

            // Decode longitude delta
            result = 0;
            shift  = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dLng = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lng += dLng;

            points.add(new LatLng(lat / 1e5, lng / 1e5));
        }
        return points;
    }

    // -----------------------------------------------------------------------
    // Duration parser: "4212s" → 4212L
    // -----------------------------------------------------------------------

    static long parseDurationSeconds(String s) {
        if (s == null || s.isEmpty()) return 0;
        String stripped = s.endsWith("s") ? s.substring(0, s.length() - 1) : s;
        try {
            return (long) Double.parseDouble(stripped);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
