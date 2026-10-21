package com.routealarm.ReachClock.trip;

import com.routealarm.ReachClock.alarm.Alarm;
import com.routealarm.ReachClock.alarm.AlarmNotFoundException;
import com.routealarm.ReachClock.alarm.AlarmRepository;
import com.routealarm.ReachClock.alarm.AlarmStatus;
import com.routealarm.ReachClock.eta.LatLng;
import com.routealarm.ReachClock.eta.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final AlarmRepository alarmRepository;
    private final RouteService routeService;

    @Transactional
    public TripResponse start(UUID userId, UUID alarmId) {
        Alarm alarm = alarmRepository.findByIdAndUserId(alarmId, userId)
                .orElseThrow(AlarmNotFoundException::new);
        if (alarm.getStatus() != AlarmStatus.ACTIVE) {
            throw new IllegalArgumentException("Alarm is not active");
        }

        // one running trip per user: stop any previous one
        for (Trip old : tripRepository.findAllByUserIdAndStatus(userId, TripStatus.ACTIVE)) {
            old.setStatus(TripStatus.CANCELLED);
            old.setEndedAt(Instant.now());
            routeService.forget(old.getId());
        }

        Trip trip = new Trip();
        trip.setUserId(userId);
        trip.setAlarmId(alarm.getId());
        trip.setDestinationLatitude(alarm.getLatitude());
        trip.setDestinationLongitude(alarm.getLongitude());
        return TripResponse.from(tripRepository.save(trip));
    }

    @Transactional(readOnly = true)
    public List<TripResponse> active(UUID userId) {
        return tripRepository.findAllByUserIdAndStatus(userId, TripStatus.ACTIVE)
                .stream().map(TripResponse::from).toList();
    }

    @Transactional
    public void cancel(UUID userId, UUID tripId) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId).orElseThrow(TripNotFoundException::new);
        if (trip.getStatus() == TripStatus.ACTIVE) {
            trip.setStatus(TripStatus.CANCELLED);
            trip.setEndedAt(Instant.now());
            routeService.forget(tripId);
        }
    }

    @Transactional(readOnly = true)
    public List<LatLng> route(UUID userId, UUID tripId, double lat, double lng) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(TripNotFoundException::new);
        return routeService.route(lat, lng, trip.getDestinationLatitude(), trip.getDestinationLongitude());
    }

    @Transactional(readOnly = true)
    public com.routealarm.ReachClock.eta.RouteDetail detailedRoute(UUID userId, UUID tripId, double lat, double lng) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(TripNotFoundException::new);
        return routeService.detailedRoute(lat, lng, trip.getDestinationLatitude(), trip.getDestinationLongitude());
    }
}
