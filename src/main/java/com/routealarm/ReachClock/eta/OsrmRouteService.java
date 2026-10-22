package com.routealarm.ReachClock.eta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.routealarm.ReachClock.geofence.GeofenceCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service("osrmRouteService")
public class OsrmRouteService implements RouteService {

    private static final Duration REFRESH = Duration.ofSeconds(15);
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofSeconds(20);
    private static final double FALLBACK_SPEED_MPS = 8.33;   // ~30 km/h city average
    private static final double DETOUR_FACTOR = 1.25;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmResponse(String code, List<OsrmRoute> routes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmRoute(double distance, double duration, OsrmGeometry geometry, List<OsrmLeg> legs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmGeometry(List<List<Double>> coordinates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmLeg(double distance, double duration, OsrmAnnotation annotation) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmAnnotation(List<Double> speed, List<Double> duration, List<Double> distance) {}

    private record Snapshot(
            double baseDurationSec,
            double roadMeters,
            double straightMeters,
            Instant at,
            List<LatLng> fullPolyline,
            int intrinsicDelaySeconds,
            String trafficCondition
    ) {}

    private final GeofenceCalculator geo;
    private final String baseUrl;
    private final RestClient client;
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> retryAt = new ConcurrentHashMap<>();
    private final Map<UUID, Double> smoothedSpeeds = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> updateCounts = new ConcurrentHashMap<>();

    public OsrmRouteService(GeofenceCalculator geo,
                            @Value("${reachclock.osrm.base-url:https://router.project-osrm.org}") String baseUrl) {
        this.geo = geo;
        this.baseUrl = baseUrl;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.client = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("Accept-Encoding", "identity")
                .defaultHeader("User-Agent", "ReachClock/1.0")
                .build();
    }

    @Override
    public EtaResult calculateEta(UUID tripId, double fromLat, double fromLng,
                                  double toLat, double toLng, Double speedMetersPerSecond) {
        Instant now = Instant.now();
        double straight = geo.distanceMeters(fromLat, fromLng, toLat, toLng);

        int count = updateCounts.compute(tripId, (k, v) -> v == null ? 1 : v + 1);

        // Update smoothed traveler speed via Exponential Moving Average (EMA)
        double currentSpeed = (speedMetersPerSecond != null && speedMetersPerSecond >= 0)
                ? speedMetersPerSecond
                : -1.0;

        Double prevSmoothed = smoothedSpeeds.get(tripId);
        double smoothedSpeed;
        if (currentSpeed >= 0) {
            smoothedSpeed = (prevSmoothed != null)
                    ? (0.35 * currentSpeed + 0.65 * prevSmoothed)
                    : currentSpeed;
            smoothedSpeeds.put(tripId, smoothedSpeed);
        } else {
            smoothedSpeed = (prevSmoothed != null) ? prevSmoothed : FALLBACK_SPEED_MPS;
        }

        Snapshot s = snapshots.get(tripId);
        boolean stale = s == null || Duration.between(s.at(), now).compareTo(REFRESH) >= 0;
        Instant blockedUntil = retryAt.get(tripId);
        boolean blocked = blockedUntil != null && now.isBefore(blockedUntil);

        if (stale && !blocked) {
            OsrmRoute r = fetch(fromLat, fromLng, toLat, toLng, true);
            if (r != null) {
                List<LatLng> points = parsePolyline(r);
                RouteAnalysis analysis = analyzeRouteTraffic(r, points);
                s = new Snapshot(r.duration(), r.distance(), straight, now, points,
                        analysis.totalDelaySeconds(), analysis.trafficCondition());
                snapshots.put(tripId, s);
                retryAt.remove(tripId);
            } else {
                retryAt.put(tripId, now.plus(RETRY_AFTER_FAILURE));
            }
        }

        double roadMeters;
        double baseDurationSec;
        int intrinsicDelaySec;
        String trafficCondition;

        if (s != null) {
            double ratio = Math.min(2.0, straight / Math.max(s.straightMeters(), 1.0));
            roadMeters = s.roadMeters() * ratio;
            baseDurationSec = s.baseDurationSec() * ratio;
            intrinsicDelaySec = (int) Math.round(s.intrinsicDelaySeconds() * ratio);
            trafficCondition = s.trafficCondition();
        } else {
            roadMeters = straight * DETOUR_FACTOR;
            baseDurationSec = roadMeters / FALLBACK_SPEED_MPS;
            intrinsicDelaySec = 0;
            trafficCondition = "SMOOTH";
        }

        // Real-time live traveler speed adjustment:
        // Only evaluate traveler delays if the user has been active for multiple updates (> 5 updates)
        // AND was moving before. Avoid marking stationary trip start as standstill!
        double liveDelaySec = 0;
        if (count > 5 && prevSmoothed != null && prevSmoothed > 3.0 && smoothedSpeed < 1.8 && roadMeters > 300) {
            // Traveler was driving normally and has now entered an unexpected jam/stoppage
            liveDelaySec = Math.min(600, (1.8 - smoothedSpeed) * 120);
            trafficCondition = "HEAVY";
        } else if (count > 5 && smoothedSpeed > 14.0) {
            // Traveler is moving fast (highway speed > 50 km/h) -> subtract delay in real time
            double speedupFactor = Math.min(1.3, smoothedSpeed / 12.0);
            baseDurationSec /= speedupFactor;
            intrinsicDelaySec = Math.max(0, intrinsicDelaySec - 60);
        }

        long adjustedSeconds = Math.max(1, Math.round(baseDurationSec + intrinsicDelaySec + liveDelaySec));
        int totalDelayMinutes = Math.max(0, (int) Math.round((intrinsicDelaySec + liveDelaySec) / 60.0));

        return new EtaResult(adjustedSeconds, roadMeters, now, trafficCondition, totalDelayMinutes);
    }

    @Override
    public List<LatLng> route(double fromLat, double fromLng, double toLat, double toLng) {
        OsrmRoute r = fetch(fromLat, fromLng, toLat, toLng, true);
        List<LatLng> points = parsePolyline(r);

        if (points.isEmpty()) {
            points.add(new LatLng(fromLat, fromLng));
            points.add(new LatLng(toLat, toLng));
        }
        return points;
    }

    @Override
    public RouteDetail detailedRoute(double fromLat, double fromLng, double toLat, double toLng) {
        OsrmRoute r = fetch(fromLat, fromLng, toLat, toLng, true);
        List<LatLng> points = parsePolyline(r);

        if (points.isEmpty()) {
            points.add(new LatLng(fromLat, fromLng));
            points.add(new LatLng(toLat, toLng));
            List<RouteSegmentDto> fallbackSegments = Collections.singletonList(
                    new RouteSegmentDto(points, "SMOOTH", 30.0, 0));
            double straight = geo.distanceMeters(fromLat, fromLng, toLat, toLng);
            return new RouteDetail(points, fallbackSegments, straight, Math.round(straight / FALLBACK_SPEED_MPS), 0, 0, "SMOOTH");
        }

        RouteAnalysis analysis = analyzeRouteTraffic(r, points);
        int delayMinutes = Math.max(0, (int) Math.round(analysis.totalDelaySeconds() / 60.0));
        return new RouteDetail(
                points,
                analysis.segments(),
                r.distance(),
                Math.round(r.duration()),
                analysis.totalDelaySeconds(),
                delayMinutes,
                analysis.trafficCondition()
        );
    }

    @Override
    public void forget(UUID tripId) {
        snapshots.remove(tripId);
        retryAt.remove(tripId);
        smoothedSpeeds.remove(tripId);
        updateCounts.remove(tripId);
    }

    private record RouteAnalysis(
            List<RouteSegmentDto> segments,
            int totalDelaySeconds,
            String trafficCondition
    ) {}

    private RouteAnalysis analyzeRouteTraffic(OsrmRoute r, List<LatLng> points) {
        List<RouteSegmentDto> segments = new ArrayList<>();
        if (r == null || points.size() < 2 || r.legs() == null || r.legs().isEmpty()) {
            segments.add(new RouteSegmentDto(points, "SMOOTH", 35.0, 0));
            return new RouteAnalysis(segments, 0, "SMOOTH");
        }

        OsrmLeg leg = r.legs().get(0);
        if (leg.annotation() == null || leg.annotation().speed() == null || leg.annotation().speed().isEmpty()) {
            segments.add(new RouteSegmentDto(points, "SMOOTH", 35.0, 0));
            return new RouteAnalysis(segments, 0, "SMOOTH");
        }

        List<Double> speeds = leg.annotation().speed();
        List<Double> durations = leg.annotation().duration();
        List<Double> distances = leg.annotation().distance();

        double totalDelay = 0;
        int n = Math.min(speeds.size(), points.size() - 1);

        String currentCondition = null;
        List<LatLng> currentPoints = new ArrayList<>();
        double currentSpeedSum = 0;
        int currentSegmentCount = 0;
        double currentSegmentDelay = 0;

        for (int i = 0; i < n; i++) {
            double s = speeds.get(i);
            double dist = (distances != null && i < distances.size()) ? distances.get(i) : 10.0;
            double dur = (durations != null && i < durations.size()) ? durations.get(i) : dist / Math.max(s, 1.0);

            // Free flow time along road (expected at least 38 km/h / 10.5 m/s)
            double freeDur = dist / Math.max(s, 10.5);
            double segDelay = Math.max(0, dur - freeDur);
            totalDelay += segDelay;

            // Classify segment condition:
            // Under ~20 km/h (5.6 m/s) -> JAM
            // 20 - 38 km/h (5.6 - 10.5 m/s) -> MODERATE
            // Above 38 km/h -> SMOOTH
            String cond;
            if (s < 5.6) {
                cond = "JAM";
            } else if (s < 10.5) {
                cond = "MODERATE";
            } else {
                cond = "SMOOTH";
            }

            if (currentCondition == null || !currentCondition.equals(cond)) {
                if (!currentPoints.isEmpty()) {
                    double avgSpeedKmh = (currentSpeedSum / Math.max(currentSegmentCount, 1)) * 3.6;
                    segments.add(new RouteSegmentDto(
                            new ArrayList<>(currentPoints),
                            currentCondition,
                            Math.round(avgSpeedKmh * 10.0) / 10.0,
                            (int) Math.round(currentSegmentDelay)
                    ));
                }
                currentCondition = cond;
                currentPoints.clear();
                currentPoints.add(points.get(i));
                currentPoints.add(points.get(i + 1));
                currentSpeedSum = s;
                currentSegmentCount = 1;
                currentSegmentDelay = segDelay;
            } else {
                currentPoints.add(points.get(i + 1));
                currentSpeedSum += s;
                currentSegmentCount++;
                currentSegmentDelay += segDelay;
            }
        }

        if (!currentPoints.isEmpty()) {
            double avgSpeedKmh = (currentSpeedSum / Math.max(currentSegmentCount, 1)) * 3.6;
            segments.add(new RouteSegmentDto(
                    currentPoints,
                    currentCondition,
                    Math.round(avgSpeedKmh * 10.0) / 10.0,
                    (int) Math.round(currentSegmentDelay)
            ));
        }

        int delaySec = (int) Math.round(totalDelay);
        double delayRatio = delaySec / Math.max(r.duration(), 1.0);
        String overallCondition;
        if (delayRatio >= 0.28) {
            overallCondition = "HEAVY";
        } else if (delayRatio >= 0.12) {
            overallCondition = "MODERATE";
        } else {
            overallCondition = "SMOOTH";
        }

        return new RouteAnalysis(segments, delaySec, overallCondition);
    }

    private List<LatLng> parsePolyline(OsrmRoute r) {
        List<LatLng> points = new ArrayList<>();
        if (r != null && r.geometry() != null && r.geometry().coordinates() != null) {
            for (List<Double> c : r.geometry().coordinates()) {
                if (c.size() >= 2) {
                    // GeoJSON coordinates are in [longitude, latitude] order
                    points.add(new LatLng(c.get(1), c.get(0)));
                }
            }
        }
        return points;
    }

    private OsrmRoute fetch(double fromLat, double fromLng, double toLat, double toLng, boolean fullGeometry) {
        try {
            String overview = fullGeometry ? "full" : "false";
            String url = String.format(Locale.ROOT,
                    "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=%s&geometries=geojson&annotations=speed,duration,distance",
                    baseUrl, fromLng, fromLat, toLng, toLat, overview);

            OsrmResponse resp = client.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(OsrmResponse.class);
            if (resp == null || !"Ok".equals(resp.code()) || resp.routes() == null || resp.routes().isEmpty()) {
                return null;
            }
            return resp.routes().get(0);
        } catch (Exception e) {
            log.warn("Routing request failed: {}", e.getMessage());
            return null;
        }
    }
}
