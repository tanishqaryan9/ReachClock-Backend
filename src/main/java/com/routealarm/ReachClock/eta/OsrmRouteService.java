package com.routealarm.ReachClock.eta;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OsrmRouteService implements RouteService {

    private static final Duration REFRESH = Duration.ofSeconds(15);
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofSeconds(30);
    private static final double FALLBACK_SPEED_MPS = 8.0;   // ~29 km/h city bus
    private static final double DETOUR_FACTOR = 1.3;

    record OsrmResponse(String code, List<OsrmRoute> routes) {}
    record OsrmRoute(double distance, double duration, OsrmGeometry geometry) {}
    record OsrmGeometry(List<List<Double>> coordinates) {}
    private record Snapshot(double durationSec, double roadMeters, double straightMeters, Instant at) {}

    private final GeofenceCalculator geo;
    private final String baseUrl;
    private final RestClient client;
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> retryAt = new ConcurrentHashMap<>();

    public OsrmRouteService(GeofenceCalculator geo, @Value("${reachclock.osrm.base-url:https://router.project-osrm.org}") String baseUrl) {
        this.geo = geo;
        this.baseUrl = baseUrl;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public EtaResult calculateEta(UUID tripId, double fromLat, double fromLng,
                                  double toLat, double toLng, Double speed) {
        Instant now = Instant.now();
        double straight = geo.distanceMeters(fromLat, fromLng, toLat, toLng);

        Snapshot s = snapshots.get(tripId);
        boolean stale = s == null || Duration.between(s.at(), now).compareTo(REFRESH) >= 0;
        Instant blockedUntil = retryAt.get(tripId);
        boolean blocked = blockedUntil != null && now.isBefore(blockedUntil);

        if (stale && !blocked) {
            OsrmRoute r = fetch(fromLat, fromLng, toLat, toLng, false);
            if (r != null) {
                s = new Snapshot(r.duration(), r.distance(), straight, now);
                snapshots.put(tripId, s);
                retryAt.remove(tripId);
            } else {
                retryAt.put(tripId, now.plus(RETRY_AFTER_FAILURE));
            }
        }

        if (s != null) {
            double ratio = Math.min(2.0, straight / Math.max(s.straightMeters(), 1.0));
            return new EtaResult(Math.round(s.durationSec() * ratio), s.roadMeters() * ratio, now);
        }

        double v = speed != null && speed > 2 ? speed : FALLBACK_SPEED_MPS;
        double road = straight * DETOUR_FACTOR;
        return new EtaResult(Math.round(road / v), road, now);
    }

    @Override
    public List<LatLng> route(double fromLat, double fromLng, double toLat, double toLng) {
        OsrmRoute r = fetch(fromLat, fromLng, toLat, toLng, true);
        List<LatLng> points = new ArrayList<>();
        if (r != null && r.geometry() != null && r.geometry().coordinates() != null) {
            for (List<Double> c : r.geometry().coordinates()) {
                points.add(new LatLng(c.get(1), c.get(0)));   // GeoJSON is [lng, lat]
            }
        }
        if (points.isEmpty()) {
            points.add(new LatLng(fromLat, fromLng));
            points.add(new LatLng(toLat, toLng));
        }
        return points;
    }

    @Override
    public void forget(UUID tripId) {
        snapshots.remove(tripId);
        retryAt.remove(tripId);
    }

    private OsrmRoute fetch(double fromLat, double fromLng, double toLat, double toLng, boolean geometry) {
        try {
            String url = String.format(Locale.ROOT,
                    "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=%s&geometries=geojson",
                    baseUrl, fromLng, fromLat, toLng, toLat, geometry ? "simplified" : "false");
            OsrmResponse resp = client.get().uri(URI.create(url)).retrieve().body(OsrmResponse.class);
            if (resp == null || !"Ok".equals(resp.code()) || resp.routes() == null || resp.routes().isEmpty()) {
                return null;
            }
            return resp.routes().get(0);
        } catch (Exception e) {
            log.warn("OSRM request failed: {}", e.getMessage());
            return null;
        }
    }
}
